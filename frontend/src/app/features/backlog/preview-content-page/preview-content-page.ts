import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize, map, of, switchMap } from 'rxjs';
import { ContentApiService } from '../../../core/api/content-api.service';
import { ContentResponse } from '../../../core/api/content';
import { SessionApiService } from '../../../core/api/session-api.service';
import { SessionResponse } from '../../../core/api/session';

const SESSION_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

@Component({
  selector: 'app-preview-content-page',
  templateUrl: './preview-content-page.html',
  styleUrl: './preview-content-page.scss',
})
export class PreviewContentPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly sessionApi = inject(SessionApiService);
  private readonly contentApi = inject(ContentApiService);
  protected sessionId: string | null = null;

  protected readonly session = signal<SessionResponse | null>(null);
  protected readonly content = signal<ContentResponse | null>(null);
  protected readonly isLoading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  ngOnInit(): void {
    const sessionId = this.route.snapshot.paramMap.get('sessionId');
    if (!sessionId || !SESSION_ID_PATTERN.test(sessionId)) {
      this.isLoading.set(false);
      this.loadError.set('This notification link does not contain a valid session.');
      return;
    }

    this.sessionId = sessionId;
    this.loadSession();
  }

  protected retry(): void {
    if (this.sessionId) this.loadSession();
  }

  protected formatDateTime(instant: string, timezone: string): string {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'full',
      timeStyle: 'short',
      timeZone: timezone,
    }).format(new Date(instant));
  }

  protected durationMinutes(session: SessionResponse): number {
    return Math.round((Date.parse(session.endsAt) - Date.parse(session.startsAt)) / 60_000);
  }

  protected reminderText(minutesBefore: number): string {
    if (minutesBefore === 0) return 'At session start';
    return `${minutesBefore} ${minutesBefore === 1 ? 'minute' : 'minutes'} before`;
  }

  protected formatDate(value: string): string {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
  }

  protected formatDuration(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return remainingSeconds === 0 ? `${minutes} min` : `${minutes}:${String(remainingSeconds).padStart(2, '0')}`;
  }

  private loadSession(): void {
    const sessionId = this.sessionId;
    if (!sessionId) return;

    this.isLoading.set(true);
    this.loadError.set(null);
    this.sessionApi.getSessionById(sessionId).pipe(
      switchMap((session) => session.content
        ? this.contentApi.getContentById(session.content.id).pipe(
          map((content) => ({ session, content })),
        )
        : of({ session, content: null })),
      finalize(() => this.isLoading.set(false)),
    ).subscribe({
      next: ({ session, content }) => {
        this.session.set(session);
        this.content.set(content);
      },
      error: (error: unknown) => {
        this.loadError.set(error instanceof HttpErrorResponse && error.status === 404
          ? 'This session or its assigned content could not be found.'
          : 'The session preview could not be loaded. Check your connection and try again.');
      },
    });
  }
}
