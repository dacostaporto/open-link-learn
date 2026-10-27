import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateSessionRequest, SessionListResponse, SessionResponse, UpdateSessionRequest } from './session';

@Injectable({ providedIn: 'root' })
export class SessionApiService {
  private readonly http = inject(HttpClient);

  createSession(request: CreateSessionRequest): Observable<SessionResponse> {
    return this.http.post<SessionResponse>('/api/v1/sessions', request);
  }

  updateSession(id: string, request: UpdateSessionRequest): Observable<SessionResponse> {
    return this.http.patch<SessionResponse>(`/api/v1/sessions/${id}`, request);
  }

  completeSession(id: string): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`/api/v1/sessions/${id}/complete`, {});
  }

  skipSession(id: string): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`/api/v1/sessions/${id}/skip`, {});
  }

  cancelSession(id: string): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`/api/v1/sessions/${id}/cancel`, {});
  }

  getSessionById(id: string): Observable<SessionResponse> {
    return this.http.get<SessionResponse>(`/api/v1/sessions/${id}`);
  }

  getSessions(from: string, to: string): Observable<SessionListResponse> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<SessionListResponse>('/api/v1/sessions', { params });
  }
}