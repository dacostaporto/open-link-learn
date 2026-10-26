import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface VapidPublicKeyResponse {
  publicKey: string;
}

export interface PushSubscriptionRequest {
  endpoint: string;
  keys: {
    p256dh: string;
    auth: string;
  };
  userAgent: string;
}

export interface PushSubscriptionResponse {
  id: string;
  active: boolean;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class PushSubscriptionApiService {
  private readonly http = inject(HttpClient);

  getVapidPublicKey(): Promise<VapidPublicKeyResponse> {
    return firstValueFrom(this.http.get<VapidPublicKeyResponse>('/api/v1/push/vapid-public-key'));
  }

  subscribe(request: PushSubscriptionRequest): Promise<PushSubscriptionResponse> {
    return firstValueFrom(this.http.post<PushSubscriptionResponse>('/api/v1/push-subscriptions', request));
  }

  unsubscribe(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/v1/push-subscriptions/${id}`));
  }
}