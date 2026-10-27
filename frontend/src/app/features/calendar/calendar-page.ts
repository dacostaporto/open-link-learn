import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnInit,
  ViewChild,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FullCalendarComponent, FullCalendarModule } from '@fullcalendar/angular';
import { CalendarOptions, DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin, { DateClickArg } from '@fullcalendar/interaction';
import luxonPlugin from '@fullcalendar/luxon3';
import timeGridPlugin from '@fullcalendar/timegrid';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription } from 'rxjs';
import { ContentApiService } from '../../core/api/content-api.service';
import { ContentResponse } from '../../core/api/content';
import { SessionApiService } from '../../core/api/session-api.service';
import { SessionResponse } from '../../core/api/session';
import { SessionSeriesApiService } from '../../core/api/session-series-api.service';
import { SettingsApiService } from '../../core/api/settings-api.service';
import { instantToLocalFields, nextWholeHour, toSessionInstant } from './session-time/session-time';
import { SessionSeriesEditPanel } from './session-series-edit-panel/session-series-edit-panel';

type SessionLifecycleAction = 'complete' | 'skip' | 'cancel';
const SESSION_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const CALENDAR_SWIPE_THRESHOLD = 60;

@Component({
  selector: 'app-calendar-page',
  imports: [FullCalendarModule, ReactiveFormsModule, RouterLink, SessionSeriesEditPanel],
  templateUrl: './calendar-page.html',
  styleUrl: './calendar-page.scss',
})
export class CalendarPage implements OnInit {
  private readonly contentApi = inject(ContentApiService);
  private readonly sessionApi = inject(SessionApiService);
  private readonly seriesApi = inject(SessionSeriesApiService);
  private readonly settingsApi = inject(SettingsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private rangeSubscription: Subscription | null = null;
  private visibleRange: { from: string; to: string } | null = null;
  private notificationSessionId: string | null = null;
  private pointerStart: { x: number; y: number; pointerId: number } | null = null;
  private shouldFocusNextSession = true;

  @ViewChild('calendar') private calendarComponent?: FullCalendarComponent;
  @ViewChild('sessionDetail') private sessionDetail?: ElementRef<HTMLElement>;
  @ViewChild('sessionComposer') private sessionComposer?: ElementRef<HTMLElement>;

  protected readonly timezone = signal<string | null>(null);
  protected readonly pendingContent = signal<ContentResponse[]>([]);
  protected readonly sessions = signal<SessionResponse[]>([]);
  protected readonly selectedSession = signal<SessionResponse | null>(null);
  protected readonly isEditingSession = signal(false);
  protected readonly isReassigningContent = signal(false);
  protected readonly isEditingSeries = signal(false);
  protected readonly isCancellingSeries = signal(false);
  protected readonly seriesActionError = signal<string | null>(null);
  protected readonly pendingLifecycleAction = signal<SessionLifecycleAction | null>(null);
  protected readonly lifecycleError = signal<string | null>(null);
  protected readonly isLoadingSessions = signal(false);
  protected readonly isLoadingContent = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly settingsError = signal<string | null>(null);
  protected readonly contentError = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly saveMessage = signal<string | null>(null);
  protected calendarOptions: CalendarOptions | null = null;

  protected readonly sessionForm = new FormGroup({
    date: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    time: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    durationMinutes: new FormControl(30, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(720), Validators.pattern(/^\d+$/)] }),
    contentItemId: new FormControl({ value: '', disabled: true }, { nonNullable: true }),
    reminderMinutesBefore: new FormControl(10, { nonNullable: true, validators: [Validators.min(0), Validators.max(1440)] }),
    assignmentCutoffMinutesBefore: new FormControl(30, { nonNullable: true, validators: [Validators.min(0), Validators.max(10080)] }),
  });

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const sessionId = params.get('sessionId');
      if (params.has('sessionId')) this.selectedSession.set(null);
      this.notificationSessionId = sessionId && SESSION_ID_PATTERN.test(sessionId) ? sessionId : null;
      const loadedSession = this.sessions().find((session) => session.id === this.notificationSessionId);
      if (loadedSession) this.selectNotificationSession(loadedSession);
    });
    this.loadSettings();
    this.loadPendingContent();
  }

  protected retrySettings(): void {
    this.loadSettings();
  }

  private loadSettings(): void {
    this.settingsError.set(null);
    this.settingsApi.getSettings().subscribe({
      next: (settings) => {
        this.timezone.set(settings.timezone);
        this.sessionForm.controls.reminderMinutesBefore.setValue(settings.defaultReminderMinutesBefore);
        this.sessionForm.controls.assignmentCutoffMinutesBefore.setValue(settings.defaultAssignmentCutoffMinutesBefore);
        this.setDefaultDateTime(settings.timezone);
        this.calendarOptions = this.createCalendarOptions(settings.timezone);
      },
      error: () => this.settingsError.set('Your planner settings could not be loaded.'),
    });
  }

  protected saveSession(): void {
    if (this.sessionForm.invalid) {
      this.sessionForm.markAllAsTouched();
      return;
    }
    const editing = this.isEditingSession();
    const reassigningContent = editing && this.isReassigningContent();
    const selected = editing ? this.selectedSession() : null;
    const timezone = selected?.timezone ?? this.timezone();
    if (!timezone) return;

    let startsAt: string;
    try {
      startsAt = toSessionInstant(
        this.sessionForm.controls.date.value,
        this.sessionForm.controls.time.value,
        timezone,
      );
    } catch {
      this.formError.set('That local time is invalid or ambiguous because of a daylight-saving change. Choose another time.');
      return;
    }

    this.isSaving.set(true);
    this.formError.set(null);
    this.saveMessage.set(null);
    const values = this.sessionForm.getRawValue();
    const request = editing && selected
      ? this.sessionApi.updateSession(selected.id, this.isReassigningContent()
        ? { contentItemId: values.contentItemId || null }
        : {
          startsAt,
          durationMinutes: values.durationMinutes,
          contentItemId: values.contentItemId || null,
        })
      : this.sessionApi.createSession({
          startsAt,
          durationMinutes: values.durationMinutes,
          timezone,
          contentItemId: values.contentItemId || null,
          reminderMinutesBefore: values.reminderMinutesBefore,
          assignmentCutoffMinutesBefore: values.assignmentCutoffMinutesBefore,
        });
    request.pipe(finalize(() => this.isSaving.set(false))).subscribe({
      next: (session) => {
        const releasedContent = editing && selected?.content !== null && selected?.content !== undefined
          && session.content === null;
        this.selectedSession.set(session);
        this.isEditingSession.set(false);
        this.isReassigningContent.set(false);
        this.saveMessage.set(releasedContent
          ? 'Assignment removed. The content is back in your backlog.'
          : editing
          ? reassigningContent ? 'Content assignment updated from the server.' : 'Session updated from the server.'
          : 'Session added to your calendar.');
        if (editing) {
          this.sessionForm.patchValue({ durationMinutes: 30, contentItemId: '' });
          if (this.timezone()) this.setDefaultDateTime(this.timezone()!);
          this.loadPendingContent();
        }
        if (this.visibleRange) this.loadSessions(this.visibleRange);
      },
      error: (error: { status?: number }) => {
        this.formError.set(error.status === 409
          ? editing
            ? 'That content is assigned elsewhere or the session changed. Refresh the calendar and try again.'
            : 'That content is already assigned to another active session.'
          : editing
            ? this.isReassigningContent()
              ? 'The assignment was not changed. Refresh the calendar and try again.'
              : 'The session was not changed. Check the date and time, then retry or refresh the calendar.'
            : 'The session could not be saved. Check the details and try again.');
      },
    });
  }

  protected editSelectedSession(): void {
    const selected = this.selectedSession();
    if (!selected || selected.status !== 'PLANNED') return;
    const local = instantToLocalFields(new Date(selected.startsAt), selected.timezone);
    const durationMinutes = Math.round((Date.parse(selected.endsAt) - Date.parse(selected.startsAt)) / 60_000);
    this.sessionForm.patchValue({
      date: local.date,
      time: local.time,
      durationMinutes,
      contentItemId: selected.content?.id ?? '',
    });
    this.isEditingSession.set(true);
    this.isReassigningContent.set(false);
    this.formError.set(null);
    this.saveMessage.set(null);
    this.focusSessionComposer();
  }

  protected reassignSelectedContent(): void {
    const selected = this.selectedSession();
    if (!selected || selected.status !== 'PLANNED') return;
    this.sessionForm.controls.contentItemId.setValue(selected.content?.id ?? '');
    this.isEditingSession.set(true);
    this.isReassigningContent.set(true);
    this.formError.set(null);
    this.saveMessage.set(null);
    this.focusSessionComposer();
  }

  protected cancelEditingSession(): void {
    this.isEditingSession.set(false);
    this.isReassigningContent.set(false);
    this.formError.set(null);
    this.sessionForm.patchValue({ durationMinutes: 30, contentItemId: '' });
    if (this.timezone()) this.setDefaultDateTime(this.timezone()!);
  }

  protected editSelectedSeries(): void {
    if (!this.selectedSession()?.seriesId) return;
    this.isEditingSession.set(false);
    this.isEditingSeries.set(true);
    this.seriesActionError.set(null);
    this.formError.set(null);
    this.saveMessage.set(null);
  }

  protected closeSeriesEditor(): void {
    this.isEditingSeries.set(false);
  }

  protected seriesUpdated(): void {
    this.isEditingSeries.set(false);
    this.saveMessage.set('Series updated. The calendar now shows the saved schedule.');
    this.refreshSeriesViews();
  }

  protected cancelSelectedSeries(): void {
    const seriesId = this.selectedSession()?.seriesId;
    if (!seriesId || this.isCancellingSeries() || this.pendingLifecycleAction()) return;
    if (!window.confirm('Cancel this entire series? Future planned occurrences will be cancelled. Completed history will remain unchanged.')) return;

    this.seriesActionError.set(null);
    this.isCancellingSeries.set(true);
    this.seriesApi.cancelSeries(seriesId)
      .pipe(finalize(() => this.isCancellingSeries.set(false)))
      .subscribe({
        next: () => {
          this.isEditingSeries.set(false);
          this.saveMessage.set('Entire series cancelled. Future planned occurrences were refreshed.');
          this.refreshSeriesViews();
        },
        error: (error: unknown) => this.seriesActionError.set(this.getSeriesCancelErrorMessage(error)),
      });
  }

  protected retrySessions(): void {
    if (this.visibleRange) this.loadSessions(this.visibleRange);
  }

  protected performLifecycleAction(action: SessionLifecycleAction): void {
    const selected = this.selectedSession();
    if (!selected || this.pendingLifecycleAction()) return;
    const isActive = selected.status === 'PLANNED' || selected.status === 'IN_PROGRESS';
    if (!isActive || (action === 'cancel' && selected.status !== 'PLANNED')) return;

    const confirmation = action === 'complete'
      ? 'Complete this session? Its assigned content will also be marked complete and moved to History.'
      : action === 'skip'
        ? 'Skip this session? Its assigned content will remain pending and be released.'
        : 'Cancel this session? Its assigned content will remain pending and be released.';
    if (!window.confirm(confirmation)) return;

    this.lifecycleError.set(null);
    this.pendingLifecycleAction.set(action);
    const request = action === 'complete'
      ? this.sessionApi.completeSession(selected.id)
      : action === 'skip'
        ? this.sessionApi.skipSession(selected.id)
        : this.sessionApi.cancelSession(selected.id);
    request.pipe(finalize(() => this.pendingLifecycleAction.set(null))).subscribe({
      next: (updated) => {
        if (this.selectedSession()?.id === selected.id) this.selectedSession.set(updated);
        this.saveMessage.set(action === 'complete'
          ? 'Session and assigned content completed.'
          : action === 'skip'
            ? 'Session skipped. Its content remains pending.'
            : 'Session cancelled. Its content remains pending.');
        this.loadPendingContent();
        if (this.visibleRange) this.loadSessions(this.visibleRange);
      },
      error: (error: unknown) => this.lifecycleError.set(this.getLifecycleErrorMessage(error, action)),
    });
  }

  protected formatSessionDate(value: string, timezone: string): string {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: timezone,
    }).format(new Date(value));
  }

  protected focusComposer(): void {
    const field = document.getElementById('session-date') ?? document.getElementById('session-content');
    field?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
    field?.focus();
  }

  protected handleCalendarPointerDown(event: PointerEvent): void {
    this.pointerStart = null;
    if (!event.isPrimary || (event.pointerType === 'mouse' && event.button !== 0)) return;
    if (event.target instanceof Element && event.target.closest('button, input, select, textarea, [role="button"], .fc-toolbar')) return;
    this.pointerStart = { x: event.clientX, y: event.clientY, pointerId: event.pointerId };
  }

  protected handleCalendarPointerUp(event: PointerEvent): void {
    const start = this.pointerStart;
    this.pointerStart = null;
    if (!start || start.pointerId !== event.pointerId) return;

    const horizontalDistance = event.clientX - start.x;
    const verticalDistance = event.clientY - start.y;
    if (Math.abs(horizontalDistance) < CALENDAR_SWIPE_THRESHOLD
      || Math.abs(horizontalDistance) <= Math.abs(verticalDistance)) return;

    const calendar = this.calendarComponent?.getApi();
    if (!calendar) return;
    if (horizontalDistance < 0) calendar.next();
    else calendar.prev();
  }

  protected cancelCalendarPointer(): void {
    this.pointerStart = null;
  }

  private isCompactViewport(): boolean {
    return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      && window.matchMedia('(max-width: 720px)').matches;
  }

  private createCalendarOptions(timezone: string): CalendarOptions {
    return {
      plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin, luxonPlugin],
      initialView: this.isCompactViewport() ? 'timeGridDay' : 'timeGridWeek',
      timeZone: timezone,
      contentHeight: 480,
      expandRows: false,
      scrollTime: '08:00:00',
      nowIndicator: true,
      selectable: false,
      dayMaxEvents: true,
      allDaySlot: false,
      slotMinTime: '06:00:00',
      slotMaxTime: '23:00:00',
      headerToolbar: {
        left: 'prev,next today',
        center: 'title',
        right: 'dayGridMonth,timeGridWeek,timeGridDay',
      },
      buttonText: { today: 'Today', month: 'Month', week: 'Week', day: 'Day' },
      datesSet: (info) => this.handleDatesSet(info),
      dateClick: (info) => this.handleDateClick(info),
      eventClick: (info) => this.handleEventClick(info),
      events: [],
      eventDisplay: 'block',
    };
  }

  private handleDatesSet(info: DatesSetArg): void {
    this.shouldFocusNextSession = true;
    this.visibleRange = { from: info.start.toISOString(), to: info.end.toISOString() };
    this.loadSessions(this.visibleRange);
  }

  private focusCalendarOnNextSession(sessions: SessionResponse[]): void {
    const calendar = this.calendarComponent?.getApi();
    if (!calendar) return;

    const now = Date.now();
    const nextSession = sessions
      .filter((session) =>
        (session.status === 'PLANNED' || session.status === 'IN_PROGRESS')
        && new Date(session.endsAt).getTime() > now,
      )
      .sort((left, right) => left.startsAt.localeCompare(right.startsAt))[0];
    const timezone = nextSession?.timezone ?? this.timezone() ?? 'UTC';
    const referenceDate = nextSession ? new Date(nextSession.startsAt) : new Date(now);
    const parts = new Intl.DateTimeFormat('en-GB', {
      timeZone: timezone,
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(referenceDate);
    const hour = Number(parts.find((part) => part.type === 'hour')?.value ?? 8);
    const minute = Number(parts.find((part) => part.type === 'minute')?.value ?? 0);
    const scrollMinutes = Math.max(6 * 60, hour * 60 + minute - 2 * 60);
    const scrollHour = Math.floor(scrollMinutes / 60);
    const scrollMinute = scrollMinutes % 60;
    calendar.scrollToTime(
      `${String(scrollHour).padStart(2, '0')}:${String(scrollMinute).padStart(2, '0')}:00`,
    );
  }

  private handleDateClick(info: DateClickArg): void {
    this.notificationSessionId = null;
    this.selectedSession.set(null);
    this.isEditingSession.set(false);
    this.isReassigningContent.set(false);
    this.lifecycleError.set(null);
    const timezone = this.timezone();
    if (!timezone || info.allDay) {
      this.sessionForm.controls.date.setValue(this.toLocalDateTime(info.date, timezone ?? 'UTC').date);
      return;
    }
    const local = this.toLocalDateTime(info.date, timezone);
    this.sessionForm.controls.date.setValue(local.date);
    this.sessionForm.controls.time.setValue(local.time);
    this.formError.set(null);
    this.saveMessage.set(null);
  }

  private handleEventClick(info: EventClickArg): void {
    const sessionId = info.event.id;
    this.notificationSessionId = null;
    this.selectedSession.set(this.sessions().find((session) => session.id === sessionId) ?? null);
    afterNextRender(() => {
      const element = this.sessionDetail?.nativeElement;
      element?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      element?.focus({ preventScroll: true });
    }, { injector: this.injector });
    this.isEditingSession.set(false);
    this.isReassigningContent.set(false);
    this.formError.set(null);
    this.lifecycleError.set(null);
    this.saveMessage.set(null);
  }

  private focusSessionComposer(): void {
    afterNextRender(() => {
      const element = this.sessionComposer?.nativeElement;
      element?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      element?.focus({ preventScroll: true });
    }, { injector: this.injector });
  }

  private toLocalDateTime(date: Date, timezone: string): { date: string; time: string } {
    return instantToLocalFields(date, timezone);
  }

  private setDefaultDateTime(timezone: string): void {
    const nextHour = nextWholeHour(timezone);
    this.sessionForm.controls.date.setValue(nextHour.date);
    this.sessionForm.controls.time.setValue(nextHour.time);
  }

  private loadPendingContent(): void {
    this.contentError.set(null);
    this.isLoadingContent.set(true);
    this.contentApi.getContent({ status: 'PENDING', page: 0, size: 100 })
      .pipe(finalize(() => this.isLoadingContent.set(false)))
      .subscribe({
        next: (result) => {
          this.pendingContent.set(result.items.filter((item) => item.assignedSessionId === null));
          this.sessionForm.controls.contentItemId.enable();
        },
        error: () => this.contentError.set('Pending content could not be loaded.'),
      });
  }

  private loadSessions(range: { from: string; to: string }): void {
    this.rangeSubscription?.unsubscribe();
    this.isLoadingSessions.set(true);
    this.loadError.set(null);
    this.rangeSubscription = this.sessionApi.getSessions(range.from, range.to)
      .pipe(finalize(() => this.isLoadingSessions.set(false)))
      .subscribe({
        next: (result) => {
          this.sessions.set(result.items);
          this.updateCalendarEvents(result.items);
          if (this.shouldFocusNextSession) {
            this.shouldFocusNextSession = false;
            this.focusCalendarOnNextSession(result.items);
          }
          const notificationSession = this.sessions().find(
            (session) => session.id === this.notificationSessionId,
          );
          if (notificationSession) {
            this.notificationSessionId = null;
            this.selectNotificationSession(notificationSession);
          }
          const selectedId = this.selectedSession()?.id;
          if (selectedId) {
            this.selectedSession.set(result.items.find((session) => session.id === selectedId) ?? null);
          }
        },
        error: () => this.loadError.set('Sessions could not be loaded for this date range.'),
      });
  }

    private selectNotificationSession(session: SessionResponse): void {
      this.selectedSession.set(session);
      this.isEditingSession.set(false);
      this.isReassigningContent.set(false);
      this.formError.set(null);
      this.lifecycleError.set(null);
      this.saveMessage.set(null);
    }

  private updateCalendarEvents(sessions: SessionResponse[]): void {
    if (!this.calendarOptions) return;
    const events: EventInput[] = sessions.map((session) => ({
      id: session.id,
      title: session.content?.title ?? 'Unassigned session',
      start: session.startsAt,
      end: session.endsAt,
      classNames: [
        `session-${session.status.toLowerCase()}`,
        ...(!session.content ? ['session-unassigned'] : []),
        ...(session.seriesId ? ['session-series-occurrence'] : []),
      ],
      extendedProps: { seriesId: session.seriesId },
    }));
    this.calendarOptions = { ...this.calendarOptions, events };
  }

  private getLifecycleErrorMessage(error: unknown, action: SessionLifecycleAction): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) return 'The session action could not reach the server. Check your connection and try again.';
      const detail = (error.error as { detail?: unknown } | null)?.detail;
      if (typeof detail === 'string') return `${detail} Refresh the calendar and try again if the session is still available.`;
    }
    const verb = action === 'complete' ? 'completed' : action === 'skip' ? 'skipped' : 'cancelled';
    return `The session could not be ${verb}. Refresh the calendar and try again.`;
  }

  private refreshSeriesViews(): void {
    if (this.visibleRange) this.loadSessions(this.visibleRange);
    this.loadPendingContent();
  }

  private getSeriesCancelErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) return 'The series could not be cancelled because the server could not be reached.';
      const detail = (error.error as { detail?: unknown } | null)?.detail;
      if (typeof detail === 'string') return `${detail} Refresh the calendar and try again.`;
    }
    return 'The entire series could not be cancelled. Refresh the calendar and try again.';
  }
}