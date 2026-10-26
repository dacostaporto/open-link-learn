import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DashboardSummary } from './dashboard';

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);

  getDashboard(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>('/api/v1/dashboard');
  }
}