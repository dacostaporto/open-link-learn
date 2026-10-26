import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateSessionSeriesRequest, SessionSeriesResponse, UpdateSessionSeriesRequest } from './session-series';

@Injectable({ providedIn: 'root' })
export class SessionSeriesApiService {
  private readonly http = inject(HttpClient);

  createSeries(request: CreateSessionSeriesRequest): Observable<SessionSeriesResponse> {
    return this.http.post<SessionSeriesResponse>('/api/v1/session-series', request);
  }

  getSeries(id: string): Observable<SessionSeriesResponse> {
    return this.http.get<SessionSeriesResponse>(`/api/v1/session-series/${id}`);
  }

  updateSeries(id: string, request: UpdateSessionSeriesRequest): Observable<SessionSeriesResponse> {
    return this.http.patch<SessionSeriesResponse>(`/api/v1/session-series/${id}`, request);
  }

  cancelSeries(id: string): Observable<void> {
    return this.http.post<void>(`/api/v1/session-series/${id}/cancel`, {});
  }
}
