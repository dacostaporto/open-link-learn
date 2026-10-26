import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import { PushSubscriptionApiService, PushSubscriptionRequest } from '../../../core/api/push-subscription-api.service';

export type PushStatus = 'off' | 'enabled' | 'denied' | 'unsupported' | 'error';
export type PushOperation = 'enable' | 'disable';

export interface PushSettingsState {
  status: PushStatus;
  message: string;
  hasSubscription: boolean;
  operation: PushOperation | null;
}

export interface StoredPushState {
  status: Exclude<PushStatus, 'unsupported'>;
  subscriptionId: string | null;
  operation?: PushOperation;
}

export interface PushBrowserPort {
  isSupported(): boolean;
  permission(): NotificationPermission;
  requestPermission(): Promise<NotificationPermission>;
  getSubscription(): Promise<PushSubscription | null>;
  subscribe(applicationServerKey: ArrayBuffer): Promise<PushSubscription>;
  userAgent(): string;
}

export interface PushStateStorage {
  read(): StoredPushState | null;
  write(state: StoredPushState): void;
  clear(): void;
}

const PUSH_STATE_KEY = 'openlinklearn.push.state';
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const PUSH_BROWSER = new InjectionToken<PushBrowserPort>('PUSH_BROWSER', {
  providedIn: 'root',
  factory: () => ({
    isSupported: () => typeof window !== 'undefined'
      && typeof navigator !== 'undefined'
      && globalThis.isSecureContext
      && 'Notification' in window
      && 'PushManager' in window
      && 'serviceWorker' in navigator,
    permission: () => Notification.permission,
    requestPermission: () => Notification.requestPermission(),
    getSubscription: async () => {
      const registration = await navigator.serviceWorker.getRegistration();
      return registration?.pushManager.getSubscription() ?? null;
    },
    subscribe: async (applicationServerKey) => {
      const registration = await navigator.serviceWorker.getRegistration();
      if (!registration) throw new Error('Service worker is not registered.');
      return registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey });
    },
    userAgent: () => navigator.userAgent,
  }),
});

export const PUSH_STATE_STORAGE = new InjectionToken<PushStateStorage>('PUSH_STATE_STORAGE', {
  providedIn: 'root',
  factory: () => ({
    read: () => {
      try {
        const raw = localStorage.getItem(PUSH_STATE_KEY);
        if (!raw) return null;
        const value: unknown = JSON.parse(raw);
        if (typeof value !== 'object' || value === null || !('status' in value)) return null;
        const stored = value as Partial<StoredPushState>;
        if (!['enabled', 'denied', 'error', 'off'].includes(stored.status ?? '')) return null;
        if (stored.subscriptionId != null
          && (typeof stored.subscriptionId !== 'string' || !UUID_PATTERN.test(stored.subscriptionId))) return null;
        return {
          status: stored.status as StoredPushState['status'],
          subscriptionId: typeof stored.subscriptionId === 'string' ? stored.subscriptionId : null,
          operation: stored.operation === 'disable' ? 'disable' : 'enable',
        };
      } catch {
        return null;
      }
    },
    write: (state) => {
      try {
        localStorage.setItem(PUSH_STATE_KEY, JSON.stringify(state));
      } catch {
        // Push remains usable when browser storage is unavailable.
      }
    },
    clear: () => {
      try {
        localStorage.removeItem(PUSH_STATE_KEY);
      } catch {
        // Push remains usable when browser storage is unavailable.
      }
    },
  }),
});

@Injectable({ providedIn: 'root' })
export class PushSettingsService {
  private readonly api = inject(PushSubscriptionApiService);
  private readonly browser = inject(PUSH_BROWSER);
  private readonly storage = inject(PUSH_STATE_STORAGE);
  private initialized = false;

  readonly state = signal<PushSettingsState>({
    status: 'off',
    message: 'Push reminders are off on this browser.',
    hasSubscription: false,
    operation: null,
  });
  readonly busy = signal(false);

  async initialize(): Promise<void> {
    if (this.initialized) return;
    this.initialized = true;

    const stored = this.storage.read();
    if (!this.browser.isSupported()) {
      this.setState('unsupported', stored?.subscriptionId ?? null);
      return;
    }

    const permission = this.browser.permission();
    if (permission === 'denied') {
      this.setState('denied', stored?.subscriptionId ?? null);
      return;
    }

    if (stored?.status === 'error') {
      this.setState('error', stored.subscriptionId, stored.operation ?? 'enable');
      return;
    }

    if (stored?.subscriptionId && permission !== 'granted') {
      this.setState('error', stored.subscriptionId, 'disable');
      return;
    }

    if (stored?.status === 'enabled' && permission === 'granted') {
      try {
        if (await this.browser.getSubscription()) {
          this.setState('enabled', stored.subscriptionId);
        } else {
          this.setState('error', stored.subscriptionId, 'disable');
        }
      } catch {
        this.setState('error', stored.subscriptionId, 'disable');
      }
      return;
    }

    this.setState('off', stored?.subscriptionId ?? null);
  }

  async enable(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    const stored = this.storage.read();
    let newSubscription: PushSubscription | null = null;

    try {
      if (!this.browser.isSupported()) {
        this.setState('unsupported', stored?.subscriptionId ?? null);
        return;
      }

      let permission = this.browser.permission();
      if (permission === 'default') permission = await this.browser.requestPermission();
      if (permission === 'denied') {
        this.setState('denied', stored?.subscriptionId ?? null);
        return;
      }
      if (permission !== 'granted') {
        this.setState('off', stored?.subscriptionId ?? null);
        return;
      }

      let subscription = await this.browser.getSubscription();
      if (!subscription) {
        const response = await this.api.getVapidPublicKey();
        subscription = await this.browser.subscribe(decodeApplicationServerKey(response.publicKey));
        newSubscription = subscription;
      }

      const result = await this.api.subscribe(this.toRequest(subscription));
      if (!result.active || !UUID_PATTERN.test(result.id)) throw new Error('Subscription was not activated.');
      this.setState('enabled', result.id);
    } catch {
      if (newSubscription) await newSubscription.unsubscribe().catch(() => false);
      this.setState('error', stored?.subscriptionId ?? null, 'enable');
    } finally {
      this.busy.set(false);
    }
  }

  async disable(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    const stored = this.storage.read();
    let subscriptionId = stored?.subscriptionId ?? null;

    try {
      const subscription = this.browser.isSupported()
        ? await this.browser.getSubscription()
        : null;

      if (!subscriptionId && subscription) {
        const response = await this.api.subscribe(this.toRequest(subscription));
        if (!UUID_PATTERN.test(response.id)) throw new Error('Subscription could not be removed.');
        subscriptionId = response.id;
      }
      if (subscriptionId) await this.api.unsubscribe(subscriptionId);
      if (subscription) await subscription.unsubscribe();
      this.setState('off', null);
    } catch {
      this.setState('error', subscriptionId, 'disable');
    } finally {
      this.busy.set(false);
    }
  }

  private setState(status: PushStatus, subscriptionId: string | null, operation?: PushOperation): void {
    const message = this.messageFor(status, operation);
    this.state.set({
      status,
      message,
      hasSubscription: subscriptionId !== null,
      operation: operation ?? null,
    });

    if (status === 'unsupported') return;
    if (status === 'off') {
      this.storage.clear();
      return;
    }
    this.storage.write({ status, subscriptionId, operation });
  }

  private messageFor(status: PushStatus, operation?: PushOperation): string {
    switch (status) {
      case 'enabled':
        return 'Push reminders are enabled for this browser.';
      case 'denied':
        return 'Notifications are blocked. Allow them for this site in your browser settings, then check again.';
      case 'unsupported':
        return 'Push needs a supported browser, a secure context, and the production service worker.';
      case 'error':
        return operation === 'disable'
          ? 'Push could not be fully removed. Retry to finish removing this browser.'
          : 'Push could not be enabled. Check your connection and VAPID setup, then retry.';
      default:
        return 'Push reminders are off on this browser.';
    }
  }

  private toRequest(subscription: PushSubscription): PushSubscriptionRequest {
    const keys = subscription.toJSON().keys;
    const p256dh = keys?.['p256dh'];
    const auth = keys?.['auth'];
    if (!subscription.endpoint || !p256dh || !auth) {
      throw new Error('The browser subscription is incomplete.');
    }
    return {
      endpoint: subscription.endpoint,
      keys: { p256dh, auth },
      userAgent: this.browser.userAgent(),
    };
  }
}

function decodeApplicationServerKey(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='));
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  if (bytes.length !== 65 || bytes[0] !== 4) throw new Error('Invalid VAPID public key.');
  const applicationServerKey = new ArrayBuffer(bytes.length);
  new Uint8Array(applicationServerKey).set(bytes);
  return applicationServerKey;
}