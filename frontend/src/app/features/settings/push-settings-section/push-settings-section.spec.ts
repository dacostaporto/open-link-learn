import { TestBed } from '@angular/core/testing';
import {
  PushSubscriptionApiService,
  PushSubscriptionRequest,
  PushSubscriptionResponse,
} from '../../../core/api/push-subscription-api.service';
import {
  PUSH_BROWSER,
  PUSH_STATE_STORAGE,
  PushBrowserPort,
  PushSettingsService,
  PushStateStorage,
  StoredPushState,
} from './push-settings.service';
import { PushSettingsSection } from './push-settings-section';

const subscriptionId = '00000000-0000-4000-8000-000000000123';

describe('PushSettingsSection', () => {
  let browser: PushBrowserPort;
  let browserState: {
    supported: boolean;
    permission: NotificationPermission;
    requestResult: NotificationPermission;
    subscription: PushSubscription | null;
    applicationServerKey: ArrayBuffer | null;
  };
  let storage: PushStateStorage;
  let storedState: StoredPushState | null;
  let api: Pick<PushSubscriptionApiService, 'getVapidPublicKey' | 'subscribe' | 'unsubscribe'>;
  let unsubscribeBrowser: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    browserState = {
      supported: true,
      permission: 'default',
      requestResult: 'granted',
      subscription: null,
      applicationServerKey: null,
    };
    storedState = null;
    unsubscribeBrowser = vi.fn(async () => true);

    browser = {
      isSupported: () => browserState.supported,
      permission: () => browserState.permission,
      requestPermission: vi.fn(async () => {
        browserState.permission = browserState.requestResult;
        return browserState.requestResult;
      }),
      getSubscription: vi.fn(async () => browserState.subscription),
      subscribe: vi.fn(async (applicationServerKey) => {
        browserState.applicationServerKey = applicationServerKey;
        browserState.subscription = createSubscription(unsubscribeBrowser);
        return browserState.subscription;
      }),
      userAgent: () => 'Open Link Learn test browser',
    };
    storage = {
      read: vi.fn(() => storedState),
      write: vi.fn((value) => { storedState = value; }),
      clear: vi.fn(() => { storedState = null; }),
    };
    api = {
      getVapidPublicKey: vi.fn(async () => ({ publicKey: vapidPublicKey() })),
      subscribe: vi.fn(async () => subscriptionResponse),
      unsubscribe: vi.fn(async () => undefined),
    };

    await TestBed.configureTestingModule({
      imports: [PushSettingsSection],
      providers: [
        PushSettingsService,
        { provide: PUSH_BROWSER, useValue: browser },
        { provide: PUSH_STATE_STORAGE, useValue: storage },
        { provide: PushSubscriptionApiService, useValue: api },
      ],
    }).compileComponents();
  });

  it('requests permission, registers the subscription and persists only status and id', async () => {
    const fixture = createPage();
    const service = TestBed.inject(PushSettingsService);
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#enable-push')!.click();
    await vi.waitFor(() => expect(service.state().status).toBe('enabled'));
    fixture.detectChanges();

    expect(browser.requestPermission).toHaveBeenCalledOnce();
    expect(api.getVapidPublicKey).toHaveBeenCalledOnce();
    expect(api.subscribe).toHaveBeenCalledWith({
      endpoint: 'https://push.example/private-endpoint',
      keys: { p256dh: 'browser-public-key', auth: 'browser-auth-secret' },
      userAgent: 'Open Link Learn test browser',
    });
    expect(new Uint8Array(browserState.applicationServerKey!)[0]).toBe(4);
    expect(storedState).toEqual({ status: 'enabled', subscriptionId, operation: undefined });
    expect(JSON.stringify(storedState)).not.toContain('push.example');
    expect(JSON.stringify(storedState)).not.toContain('browser-auth-secret');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Push reminders are enabled');
    expect((fixture.nativeElement as HTMLElement).querySelector('#disable-push')).not.toBeNull();
  });

  it('persists denied permission and gives browser-settings guidance without calling the API', async () => {
    browserState.requestResult = 'denied';
    const fixture = createPage();
    const service = TestBed.inject(PushSettingsService);
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#enable-push')!.click();
    await vi.waitFor(() => expect(service.state().status).toBe('denied'));
    fixture.detectChanges();

    expect(storedState?.status).toBe('denied');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Allow them for this site in your browser settings');
    expect(api.getVapidPublicKey).not.toHaveBeenCalled();
    expect(api.subscribe).not.toHaveBeenCalled();
  });

  it('checks server push delivery before treating a saved subscription as enabled', async () => {
    storedState = { status: 'enabled', subscriptionId };
    browserState.permission = 'granted';
    browserState.subscription = createSubscription(unsubscribeBrowser);
    api.getVapidPublicKey = vi.fn(async () => {
      throw new Error('Web Push is disabled on the server.');
    });
    const fixture = createPage();
    const service = TestBed.inject(PushSettingsService);
    await vi.waitFor(() => expect(service.state().status).toBe('error'));
    fixture.detectChanges();

    expect(service.state().message).toContain('server VAPID settings');
    expect(service.state().hasSubscription).toBe(true);
    expect((fixture.nativeElement as HTMLElement).querySelector('#remove-push')).not.toBeNull();
  });

  it('does not throw or offer enable controls when browser push is unsupported', async () => {
    browserState.supported = false;
    const fixture = createPage();
    const service = TestBed.inject(PushSettingsService);
    await service.initialize();
    fixture.detectChanges();

    expect(service.state().status).toBe('unsupported');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('supported browser, a secure context');
    expect((fixture.nativeElement as HTMLElement).querySelector('#enable-push')).toBeNull();
    expect(api.getVapidPublicKey).not.toHaveBeenCalled();
  });

  it('persists a safe error and rolls back a new browser subscription when registration fails', async () => {
    api.subscribe = vi.fn(async (_request: PushSubscriptionRequest) => {
      throw new Error('private-endpoint and browser-auth-secret');
    });
    const fixture = createPage();
    const service = TestBed.inject(PushSettingsService);
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#enable-push')!.click();
    await vi.waitFor(() => expect(service.state().status).toBe('error'));
    fixture.detectChanges();

    expect(unsubscribeBrowser).toHaveBeenCalledOnce();
    expect(storedState?.status).toBe('error');
    expect(service.state().message).not.toContain('private-endpoint');
    expect(service.state().message).not.toContain('browser-auth-secret');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Check the server VAPID settings and connection');
  });

  it('keeps the server id and browser subscription when removal fails so removal can be retried', async () => {
    storedState = { status: 'enabled', subscriptionId };
    browserState.permission = 'granted';
    browserState.subscription = createSubscription(unsubscribeBrowser);
    api.unsubscribe = vi.fn(async (_id: string) => {
      throw new Error('server rejected request');
    });
    const fixture = createPage();
    const service = TestBed.inject(PushSettingsService);
    await service.initialize();
    await vi.waitFor(() => expect(service.state().status).toBe('enabled'));
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#disable-push')!.click();
    await vi.waitFor(() => expect(service.state().status).toBe('error'));
    fixture.detectChanges();

    expect(api.unsubscribe).toHaveBeenCalledWith(subscriptionId);
    expect(unsubscribeBrowser).not.toHaveBeenCalled();
    expect(storedState?.subscriptionId).toBe(subscriptionId);
    expect((fixture.nativeElement as HTMLElement).querySelector('#retry-remove-push')).not.toBeNull();
  });

  it('removes the server registration and browser subscription when disabling succeeds', async () => {
    storedState = { status: 'enabled', subscriptionId };
    browserState.permission = 'granted';
    browserState.subscription = createSubscription(unsubscribeBrowser);
    const fixture = createPage();
    const service = TestBed.inject(PushSettingsService);
    await vi.waitFor(() => expect(service.state().status).toBe('enabled'));
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#disable-push')!.click();
    await vi.waitFor(() => expect(service.state().status).toBe('off'));

    expect(api.unsubscribe).toHaveBeenCalledWith(subscriptionId);
    expect(unsubscribeBrowser).toHaveBeenCalledOnce();
    expect(storedState).toBeNull();
  });

  function createPage() {
    const fixture = TestBed.createComponent(PushSettingsSection);
    fixture.detectChanges();
    return fixture;
  }
});

function createSubscription(unsubscribe: ReturnType<typeof vi.fn>): PushSubscription {
  return {
    endpoint: 'https://push.example/private-endpoint',
    toJSON: () => ({
      endpoint: 'https://push.example/private-endpoint',
      keys: { p256dh: 'browser-public-key', auth: 'browser-auth-secret' },
    }),
    unsubscribe,
  } as unknown as PushSubscription;
}

function vapidPublicKey(): string {
  const bytes = [4, ...Array.from({ length: 64 }, (_, index) => index + 1)];
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

const subscriptionResponse: PushSubscriptionResponse = {
  id: subscriptionId,
  active: true,
  createdAt: '2026-10-23T00:00:00Z',
};