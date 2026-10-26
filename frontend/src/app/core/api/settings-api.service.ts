import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Settings } from './settings';

@Injectable({ providedIn: 'root' })
export class SettingsApiService {
  private readonly http = inject(HttpClient);

  getSettings(): Observable<Settings> {
    return this.http.get<Settings>('/api/v1/settings');
  }

  updateSettings(settings: Settings): Observable<Settings> {
    return this.http.patch<Settings>('/api/v1/settings', settings);
  }
}