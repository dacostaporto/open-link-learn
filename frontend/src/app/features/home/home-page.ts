import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AddContentPage } from '../backlog/add-content-page/add-content-page';
import { DashboardApiService } from '../../core/api/dashboard-api.service';
import { DashboardSummary } from '../../core/api/dashboard';
import { SettingsApiService } from '../../core/api/settings-api.service';
import { Settings } from '../../core/api/settings';

@Component({
  imports: [AddContentPage, RouterLink],
  selector: 'app-home-page',
  styleUrl: './home-page.scss',
  templateUrl: './home-page.html',
})
export class HomePage {
  protected readonly dashboard = signal<DashboardSummary | null>(null);
  protected readonly loadingDashboard = signal(true);
  protected readonly dashboardError = signal(false);
  protected readonly settings = signal<Settings | null>(null);
  protected readonly loadingSettings = signal(true);
  protected readonly settingsUnavailable = signal(false);

  private readonly dashboardApi = inject(DashboardApiService);
  private readonly settingsApi = inject(SettingsApiService);

  constructor() {
    this.loadDashboard();
    this.loadSettings();
  }

  protected loadDashboard(): void {
    this.loadingDashboard.set(true);
    this.dashboardError.set(false);
    this.dashboardApi.getDashboard().pipe(finalize(() => this.loadingDashboard.set(false))).subscribe({
      next: (dashboard) => this.dashboard.set(dashboard),
      error: () => this.dashboardError.set(true),
    });
  }

  protected loadSettings(): void {
    this.loadingSettings.set(true);
    this.settingsUnavailable.set(false);
    this.settingsApi.getSettings().pipe(finalize(() => this.loadingSettings.set(false))).subscribe({
      next: (settings) => this.settings.set(settings),
      error: () => this.settingsUnavailable.set(true),
    });
  }

  protected handleContentAdded(): void {
    this.loadDashboard();
  }

  protected formatNextSession(startsAt: string): string {
    const timeZone = this.settings()?.timezone ?? 'UTC';
    const options: Intl.DateTimeFormatOptions = {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      timeZone,
    };

    try {
      return new Intl.DateTimeFormat('en', options).format(new Date(startsAt));
    } catch {
      return new Intl.DateTimeFormat('en', { ...options, timeZone: 'UTC' }).format(new Date(startsAt));
    }
  }
}