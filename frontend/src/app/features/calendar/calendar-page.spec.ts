import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { CalendarPage } from './calendar-page';
import { ContentResponse } from '../../core/api/content';
import { SessionResponse } from '../../core/api/session';

describe('CalendarPage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CalendarPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'calendar', component: CalendarPage }]),
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    vi.restoreAllMocks();
  });

  it('navigates to the adjacent calendar range on horizontal touch and mouse swipes', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    const previous = vi.fn();
    const next = vi.fn();
    page.calendarComponent = { getApi: () => ({ prev: previous, next }) };
    const surface = fixture.nativeElement.querySelector('.calendar-gesture-surface') as HTMLElement;

    page.handleCalendarPointerDown(pointerEvent(surface, 240, 100, 'touch'));
    page.handleCalendarPointerUp(pointerEvent(surface, 150, 110, 'touch'));
    page.handleCalendarPointerDown(pointerEvent(surface, 150, 100, 'mouse'));
    page.handleCalendarPointerUp(pointerEvent(surface, 240, 110, 'mouse'));

    expect(next).toHaveBeenCalledOnce();
    expect(previous).toHaveBeenCalledOnce();
  });

  it('ignores vertical movement and gestures started on calendar controls', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    const previous = vi.fn();
    const next = vi.fn();
    page.calendarComponent = { getApi: () => ({ prev: previous, next }) };
    const surface = fixture.nativeElement.querySelector('.calendar-gesture-surface') as HTMLElement;
    const control = document.createElement('button');

    page.handleCalendarPointerDown(pointerEvent(surface, 100, 100, 'touch'));
    page.handleCalendarPointerUp(pointerEvent(surface, 180, 190, 'touch'));
    page.handleCalendarPointerDown(pointerEvent(control, 240, 100, 'mouse'));
    page.handleCalendarPointerUp(pointerEvent(surface, 150, 110, 'mouse'));

    expect(previous).not.toHaveBeenCalled();
    expect(next).not.toHaveBeenCalled();
  });

  it('limits the calendar height and scrolls to two hours before the next active session', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    const scrollToTime = vi.fn();
    page.calendarComponent = { getApi: () => ({ scrollToTime }) };

    page.focusCalendarOnNextSession([
      {
        ...plannedSession,
        startsAt: '2099-10-28T07:00:00Z',
        endsAt: '2099-10-28T07:30:00Z',
        status: 'CANCELLED',
      },
      {
        ...plannedSession,
        startsAt: '2099-10-28T09:00:00Z',
        endsAt: '2099-10-28T09:30:00Z',
      },
      {
        ...plannedSession,
        startsAt: '2099-10-28T13:00:00Z',
        endsAt: '2099-10-28T13:30:00Z',
      },
    ]);

    expect(page.calendarOptions.contentHeight).toBe(480);
    expect(page.calendarOptions.expandRows).toBe(false);
    expect(scrollToTime).toHaveBeenCalledWith('08:00:00');
  });

  it('scrolls to and focuses the session details when a calendar event is selected', async () => {
    const originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
    const scrollIntoView = vi.fn();
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    });

    try {
      const fixture = createCalendar();
      const page = fixture.componentInstance as any;
      page.handleEventClick({ event: { id: plannedSession.id } });
      fixture.detectChanges();
      await fixture.whenStable();

      const detail = fixture.nativeElement.querySelector('.session-detail') as HTMLElement;
      expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'center' });
      expect(document.activeElement).toBe(detail);
    } finally {
      if (originalScrollIntoView) {
        Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
          configurable: true,
          value: originalScrollIntoView,
        });
      } else {
        Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView');
      }
    }
  });

  it('scrolls to and highlights the composer when editing a session', async () => {
    const originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
    const scrollIntoView = vi.fn();
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    });

    try {
      const fixture = createCalendar();
      const page = fixture.componentInstance as any;
      page.selectedSession.set(plannedSession);
      fixture.detectChanges();

      buttonWithText(fixture.nativeElement, 'Edit session').click();
      fixture.detectChanges();
      await fixture.whenStable();

      const composer = fixture.nativeElement.querySelector('.session-composer') as HTMLElement;
      expect(composer.classList).toContain('is-editing');
      expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'center' });
      expect(document.activeElement).toBe(composer);

      (fixture.nativeElement.querySelector('.session-composer .secondary-button') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(composer.classList).not.toContain('is-editing');
    } finally {
      if (originalScrollIntoView) {
        Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
          configurable: true,
          value: originalScrollIntoView,
        });
      } else {
        Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView');
      }
    }
  });

  it('creates a one-off session and shows the saved session', () => {
    const fixture = createCalendar([pendingItem]);
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#session-date', '2026-10-28');
    setInput(element, '#session-time', '10:00');
    setInput(element, '#session-duration', '45');
    const assignment = element.querySelector('#session-content') as HTMLSelectElement;
    assignment.value = pendingItem.id;
    assignment.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();

    const submitButton = element.querySelector('.submit-button') as HTMLButtonElement;
    submitButton.click();
    fixture.detectChanges();
    expect(submitButton.disabled).toBe(true);
    expect(submitButton.textContent).toContain('Saving...');

    const create = httpTesting.expectOne('/api/v1/sessions');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({
      startsAt: '2026-10-28T09:00:00Z',
      durationMinutes: 45,
      timezone: 'Europe/Madrid',
      contentItemId: pendingItem.id,
      reminderMinutesBefore: 10,
      assignmentCutoffMinutesBefore: 30,
    });
    const createdSession: SessionResponse = {
      ...plannedSession,
      startsAt: '2026-10-28T09:00:00Z',
      endsAt: '2026-10-28T09:45:00Z',
      content: { id: pendingItem.id, title: pendingItem.title, url: pendingItem.url, type: pendingItem.type },
    };
    create.flush(createdSession);
    const refresh = httpTesting.expectOne((request) => request.url === '/api/v1/sessions' && request.method === 'GET');
    refresh.flush({ items: [createdSession] });
    fixture.detectChanges();

    expect(element.textContent).toContain('Session added to your calendar.');
    expect(element.querySelector('.session-detail h2')?.textContent).toContain(pendingItem.title);
    expect(submitButton.disabled).toBe(false);
  });

  it('preserves session details and reports a create failure', () => {
    const fixture = createCalendar();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#session-date', '2026-10-28');
    setInput(element, '#session-time', '10:00');
    fixture.detectChanges();
    (element.querySelector('.submit-button') as HTMLButtonElement).click();

    httpTesting.expectOne('/api/v1/sessions').flush(
      { detail: 'The session could not be saved.' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('The session could not be saved.');
    expect((element.querySelector('#session-date') as HTMLInputElement).value).toBe('2026-10-28');
    expect((element.querySelector('#session-time') as HTMLInputElement).value).toBe('10:00');
    expect(httpTesting.match((request) => request.url === '/api/v1/sessions' && request.method === 'GET')).toHaveLength(0);
  });

  it('exposes required date and time errors when a session is submitted incomplete', () => {
    const fixture = createCalendar();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#session-date', '');
    setInput(element, '#session-time', '');
    fixture.detectChanges();
    (element.querySelector('.submit-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    const date = element.querySelector('#session-date') as HTMLInputElement;
    const time = element.querySelector('#session-time') as HTMLInputElement;
    expect(date.getAttribute('aria-invalid')).toBe('true');
    expect(date.getAttribute('aria-describedby')).toBe('session-date-error');
    expect(time.getAttribute('aria-invalid')).toBe('true');
    expect(time.getAttribute('aria-describedby')).toBe('session-time-error');
    expect(element.querySelector('#session-date-error')?.textContent).toContain('Choose a date.');
    expect(element.querySelector('#session-time-error')?.textContent).toContain('Choose a start time.');
    httpTesting.expectNone('/api/v1/sessions');
  });

  it('prefills the session and refreshes the visible range from the update response', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    page.selectedSession.set(plannedSession);
    page.sessions.set([plannedSession]);
    fixture.detectChanges();

    buttonWithText(fixture.nativeElement, 'Edit session').click();
    fixture.detectChanges();
    const date = fixture.nativeElement.querySelector('#session-date') as HTMLInputElement;
    const time = fixture.nativeElement.querySelector('#session-time') as HTMLInputElement;
    const duration = fixture.nativeElement.querySelector('#session-duration') as HTMLInputElement;
    expect(date.value).toBe('2026-10-26');
    expect(time.value).toBe('10:00');
    expect(duration.value).toBe('75');

    date.value = '2026-10-28';
    date.dispatchEvent(new Event('input'));
    time.value = '12:20';
    time.dispatchEvent(new Event('input'));
    duration.value = '90';
    duration.dispatchEvent(new Event('input'));
    const assignment = fixture.nativeElement.querySelector('#session-content') as HTMLSelectElement;
    assignment.value = '';
    assignment.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.submit-button') as HTMLButtonElement).click();

    const update = httpTesting.expectOne(`/api/v1/sessions/${plannedSession.id}`);
    expect(update.request.method).toBe('PATCH');
    expect(update.request.body).toEqual({
      startsAt: '2026-10-28T11:20:00Z',
      durationMinutes: 90,
      contentItemId: null,
    });
    const updatedSession = {
      ...plannedSession,
      startsAt: '2026-10-28T11:20:00Z',
      endsAt: '2026-10-28T12:50:00Z',
      content: null,
    };
    update.flush(updatedSession);
    const refresh = httpTesting.expectOne((request) => request.url === '/api/v1/sessions');
    expect(refresh.request.params.get('from')).toBe(page.visibleRange.from);
    expect(refresh.request.params.get('to')).toBe(page.visibleRange.to);
    refresh.flush({ items: [updatedSession] });
    httpTesting.expectOne((request) => request.url === '/api/v1/content').flush({ items: [] });
    fixture.detectChanges();

    expect(page.selectedSession()).toEqual(updatedSession);
    expect(page.sessions()).toEqual([updatedSession]);
    expect(fixture.nativeElement.textContent)
      .toContain('Assignment removed. The content is back in your backlog.');
    expect(page.calendarOptions.events[0].start).toBe(updatedSession.startsAt);
  });

  it('opens the session selected by a valid notification query parameter', async () => {
    await TestBed.inject(Router).navigateByUrl(`/calendar?sessionId=${plannedSession.id}`);
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    fixture.detectChanges();

    expect(page.selectedSession()).toEqual(plannedSession);
    expect(fixture.nativeElement.querySelector('.session-detail h2')?.textContent).toBe('Listening lesson');
  });

  it('ignores malformed notification session IDs', async () => {
    await TestBed.inject(Router).navigateByUrl(`/calendar?sessionId=${plannedSession.id}`);
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    expect(page.selectedSession()).toEqual(plannedSession);

    await TestBed.inject(Router).navigateByUrl('/calendar?sessionId=..%2Fsettings');

    expect(page.selectedSession()).toBeNull();
  });

  it('keeps the server-confirmed event visible and the editor open when an update fails', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    page.selectedSession.set(plannedSession);
    page.sessions.set([plannedSession]);
    page.calendarOptions = { ...page.calendarOptions, events: [eventFor(plannedSession)] };
    fixture.detectChanges();

    buttonWithText(fixture.nativeElement, 'Edit session').click();
    fixture.detectChanges();
    const time = fixture.nativeElement.querySelector('#session-time') as HTMLInputElement;
    time.value = '11:30';
    time.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.submit-button') as HTMLButtonElement).click();

    httpTesting.expectOne(`/api/v1/sessions/${plannedSession.id}`).flush(
      { detail: 'The session has changed.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(page.selectedSession()).toEqual(plannedSession);
    expect(page.sessions()).toEqual([plannedSession]);
    expect(page.calendarOptions.events[0].start).toBe(plannedSession.startsAt);
    expect(fixture.nativeElement.querySelector('#session-time')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('Refresh the calendar and try again.');
    httpTesting.expectNone((request) => request.url === '/api/v1/sessions' && request.method === 'GET');
  });

  it('rejects fractional durations before sending an update', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    page.selectedSession.set(plannedSession);
    page.sessions.set([plannedSession]);
    fixture.detectChanges();
    buttonWithText(fixture.nativeElement, 'Edit session').click();
    fixture.detectChanges();

    const duration = fixture.nativeElement.querySelector('#session-duration') as HTMLInputElement;
    duration.value = '20.5';
    duration.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.submit-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('whole number of minutes from 1 to 720');
    httpTesting.expectNone(`/api/v1/sessions/${plannedSession.id}`);
  });

  it.each([
    ['complete', 'COMPLETED'],
    ['skip', 'SKIPPED'],
    ['cancel', 'CANCELLED'],
  ] as const)('confirms and applies the %s action, then refreshes server state', (action, status) => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = createCalendar([pendingItem]);
    const page = fixture.componentInstance as any;
    page.selectedSession.set(plannedSession);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector(`[data-action="${action}"]`) as HTMLButtonElement).click();
    expect(confirmation).toHaveBeenCalledOnce();
    expect(confirmation.mock.calls[0][0]).toContain(action === 'complete'
      ? 'marked complete and moved to History'
      : 'remain pending and be released');
    if (action === 'complete') {
      const completeButton = fixture.nativeElement.querySelector('[data-action="complete"]') as HTMLButtonElement;
      fixture.detectChanges();
      expect(completeButton.disabled).toBe(true);
      expect(completeButton.textContent).toContain('Completing...');
    }

    const request = httpTesting.expectOne(`/api/v1/sessions/${plannedSession.id}/${action}`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    const updatedSession: SessionResponse = {
      ...plannedSession,
      status,
      content: action === 'complete' ? plannedSession.content : null,
      completedAt: action === 'complete' ? '2026-10-26T10:00:00Z' : null,
    };
    request.flush(updatedSession);

    const rangeRefresh = httpTesting.expectOne((req) => req.url === '/api/v1/sessions' && req.method === 'GET');
    rangeRefresh.flush({ items: [updatedSession] });
    const backlogRefresh = httpTesting.expectOne((req) => req.url === '/api/v1/content' && req.method === 'GET');
    expect(backlogRefresh.request.params.get('status')).toBe('PENDING');
    backlogRefresh.flush({ items: action === 'complete' ? [] : [pendingItem] });
    fixture.detectChanges();

    expect(page.selectedSession().status).toBe(status);
    expect(page.pendingContent()).toEqual(action === 'complete' ? [] : [pendingItem]);
    if (action !== 'complete') {
      expect(updatedSession.content).toBeNull();
      httpTesting.expectNone(`/api/v1/content/${pendingItem.id}/complete`);
    }
  });

  it.each(['complete', 'skip', 'cancel'] as const)('shows server errors for a failed %s action without changing state', (action) => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    page.selectedSession.set(plannedSession);
    page.sessions.set([plannedSession]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector(`[data-action="${action}"]`) as HTMLButtonElement).click();
    httpTesting.expectOne(`/api/v1/sessions/${plannedSession.id}/${action}`).flush(
      { detail: 'This session cannot transition from its current state.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(page.selectedSession()).toEqual(plannedSession);
    expect(page.sessions()).toEqual([plannedSession]);
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('Refresh the calendar and try again');
    httpTesting.expectNone((req) => req.url === '/api/v1/sessions' && req.method === 'GET');
    httpTesting.expectNone((req) => req.url === '/api/v1/content' && req.method === 'GET');
  });

  it('reassigns content using only the assignment field and refreshes pending content', () => {
    const fixture = createCalendar([pendingItem]);
    const page = fixture.componentInstance as any;
    page.selectedSession.set(plannedSession);
    page.sessions.set([plannedSession]);
    fixture.detectChanges();
    buttonWithText(fixture.nativeElement, 'Reassign content').click();
    fixture.detectChanges();

    const assignment = fixture.nativeElement.querySelector('#session-content') as HTMLSelectElement;
    assignment.value = pendingItem.id;
    assignment.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#session-date')).toBeNull();
    (fixture.nativeElement.querySelector('.submit-button') as HTMLButtonElement).click();

    const update = httpTesting.expectOne(`/api/v1/sessions/${plannedSession.id}`);
    expect(update.request.method).toBe('PATCH');
    expect(update.request.body).toEqual({ contentItemId: pendingItem.id });
    const reassigned = {
      ...plannedSession,
      content: { id: pendingItem.id, title: pendingItem.title, url: pendingItem.url, type: pendingItem.type },
    };
    update.flush(reassigned);
    httpTesting.expectOne((req) => req.url === '/api/v1/sessions' && req.method === 'GET').flush({ items: [reassigned] });
    httpTesting.expectOne((req) => req.url === '/api/v1/content' && req.method === 'GET').flush({ items: [] });
    fixture.detectChanges();

    expect(page.selectedSession().content.id).toBe(pendingItem.id);
    expect(page.pendingContent()).toEqual([]);
  });

  it('removes an assignment, returns its content to backlog, and refreshes the calendar', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    page.selectedSession.set(plannedSession);
    page.sessions.set([plannedSession]);
    fixture.detectChanges();
    buttonWithText(fixture.nativeElement, 'Reassign content').click();
    fixture.detectChanges();

    const assignment = fixture.nativeElement.querySelector('#session-content') as HTMLSelectElement;
    assignment.value = '';
    assignment.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.submit-button') as HTMLButtonElement).click();

    const update = httpTesting.expectOne(`/api/v1/sessions/${plannedSession.id}`);
    expect(update.request.body).toEqual({ contentItemId: null });
    const released = { ...plannedSession, content: null };
    update.flush(released);
    httpTesting.expectOne((req) => req.url === '/api/v1/sessions' && req.method === 'GET')
      .flush({ items: [released] });
    httpTesting.expectOne((req) => req.url === '/api/v1/content' && req.method === 'GET')
      .flush({ items: [{ ...assignedItem, assignedSessionId: null }] });
    fixture.detectChanges();

    expect(page.selectedSession().content).toBeNull();
    expect(page.pendingContent()).toEqual([{ ...assignedItem, assignedSessionId: null }]);
    expect(fixture.nativeElement.textContent).toContain('Assignment removed. The content is back in your backlog.');
    expect(page.calendarOptions.events[0].title).toBe('Unassigned session');
  });

  it('clearly labels an unassigned session when no backlog items are available', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    const unassignedSession = { ...plannedSession, content: null };
    page.selectedSession.set(unassignedSession);
    page.sessions.set([unassignedSession]);
    page.updateCalendarEvents([unassignedSession]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.session-detail h2')?.textContent)
      .toContain('Unassigned session');
    expect(fixture.nativeElement.querySelector('.assignment-empty')?.textContent)
      .toContain('This session remains unassigned.');
    expect(page.calendarOptions.events[0].title).toBe('Unassigned session');
    expect(page.calendarOptions.events[0].classNames).toContain('session-unassigned');
  });

  it('keeps the previous assignment and reports a conflict when reassignment fails', () => {
    const fixture = createCalendar([pendingItem]);
    const page = fixture.componentInstance as any;
    page.selectedSession.set(plannedSession);
    fixture.detectChanges();
    buttonWithText(fixture.nativeElement, 'Reassign content').click();
    fixture.detectChanges();
    const assignment = fixture.nativeElement.querySelector('#session-content') as HTMLSelectElement;
    assignment.value = pendingItem.id;
    assignment.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.submit-button') as HTMLButtonElement).click();

    httpTesting.expectOne(`/api/v1/sessions/${plannedSession.id}`).flush(
      { detail: 'Content is already assigned to another active session.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(page.selectedSession()).toEqual(plannedSession);
    expect(fixture.nativeElement.querySelector('#session-content')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('Refresh the calendar and try again.');
    httpTesting.expectNone((req) => req.url === '/api/v1/sessions' && req.method === 'GET');
  });

  it('does not call the server when a lifecycle confirmation is declined', () => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    page.selectedSession.set(plannedSession);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-action="complete"]') as HTMLButtonElement).click();

    expect(confirmation).toHaveBeenCalledOnce();
    expect(page.selectedSession()).toEqual(plannedSession);
    httpTesting.expectNone((req) => req.url.includes('/complete') || req.url.includes('/skip') || req.url.includes('/cancel'));
  });

  it('shows the series identity and keeps series actions distinct from occurrence actions', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    const occurrence = { ...plannedSession, seriesId: 'series-1' };
    page.selectedSession.set(occurrence);
    page.sessions.set([occurrence]);
    page.updateCalendarEvents([occurrence]);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.series-identity')?.textContent).toContain('series-1');
    expect(page.calendarOptions.events[0].classNames).toContain('session-series-occurrence');
    expect(page.calendarOptions.events[0].extendedProps.seriesId).toBe('series-1');
    expect(element.querySelector('[data-action="cancel"]')?.textContent).toContain('Cancel session');
    expect(element.querySelector('[data-series-action="cancel"]')?.textContent).toContain('Cancel entire series');
    expect(element.querySelector('[aria-label="Recurring series actions"]')?.textContent)
      .toContain('Applies to the entire series');
  });

  it('loads and edits the entire series, then refreshes the visible calendar range', () => {
    const fixture = createCalendar();
    const page = fixture.componentInstance as any;
    const occurrence = { ...plannedSession, seriesId: 'series-1' };
    page.selectedSession.set(occurrence);
    page.sessions.set([occurrence]);
    page.updateCalendarEvents([occurrence]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-series-action="edit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/session-series/series-1').flush(seriesDetails);
    fixture.detectChanges();
    const duration = fixture.nativeElement.querySelector('#edit-series-duration') as HTMLInputElement;
    duration.value = '60';
    duration.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.series-editor .submit-button') as HTMLButtonElement).click();

    const update = httpTesting.expectOne('/api/v1/session-series/series-1');
    expect(update.request.method).toBe('PATCH');
    expect(update.request.body).toEqual({
      applyTo: 'ENTIRE_SERIES',
      startDate: '2026-10-26',
      endDate: null,
      startTime: '09:30:00',
      durationMinutes: 60,
      timezone: 'Europe/Madrid',
      daysOfWeek: ['MONDAY', 'WEDNESDAY'],
      reminderMinutesBefore: 10,
      assignmentCutoffMinutesBefore: 30,
    });
    update.flush({ ...seriesDetails, durationMinutes: 60 });

    const refresh = httpTesting.expectOne((request) => request.url === '/api/v1/sessions');
    expect(refresh.request.params.get('from')).toBe(page.visibleRange.from);
    expect(refresh.request.params.get('to')).toBe(page.visibleRange.to);
    const updatedOccurrence = { ...occurrence, endsAt: '2026-10-26T10:00:00Z' };
    refresh.flush({ items: [updatedOccurrence] });
    httpTesting.expectOne((request) => request.url === '/api/v1/content').flush({ items: [] });
    fixture.detectChanges();

    expect(page.selectedSession()).toEqual(updatedOccurrence);
    expect(page.calendarOptions.events[0].end).toBe(updatedOccurrence.endsAt);
    expect(fixture.nativeElement.textContent).toContain('Series updated.');
    expect(fixture.nativeElement.querySelector('.series-editor')).toBeNull();
  });

  it('confirms cancellation of the entire series and refreshes sessions and pending content', () => {
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = createCalendar([pendingItem]);
    const page = fixture.componentInstance as any;
    const occurrence = { ...plannedSession, seriesId: 'series-1' };
    page.selectedSession.set(occurrence);
    page.sessions.set([occurrence]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-series-action="cancel"]') as HTMLButtonElement).click();
    expect(confirmation.mock.calls[0][0]).toContain('Cancel this entire series');
    httpTesting.expectOne('/api/v1/session-series/series-1/cancel').flush(null);

    const refresh = httpTesting.expectOne((request) => request.url === '/api/v1/sessions');
    expect(refresh.request.params.get('from')).toBe(page.visibleRange.from);
    expect(refresh.request.params.get('to')).toBe(page.visibleRange.to);
    const cancelledOccurrence = { ...occurrence, status: 'CANCELLED' as const, content: null };
    refresh.flush({ items: [cancelledOccurrence] });
    httpTesting.expectOne((request) => request.url === '/api/v1/content').flush({ items: [pendingItem] });
    fixture.detectChanges();

    expect(page.selectedSession()).toEqual(cancelledOccurrence);
    expect(page.pendingContent()).toEqual([pendingItem]);
    expect(fixture.nativeElement.textContent).toContain('Entire series cancelled.');
  });
});

function buttonWithText(root: ParentNode, text: string): HTMLButtonElement {
  return [...root.querySelectorAll('button')]
    .find((button) => button.textContent?.trim() === text) as HTMLButtonElement;
}

function setInput(element: HTMLElement, selector: string, value: string): void {
  const input = element.querySelector(selector) as HTMLInputElement;
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

function pointerEvent(
  target: EventTarget,
  clientX: number,
  clientY: number,
  pointerType: string,
): PointerEvent {
  return {
    target,
    clientX,
    clientY,
    pointerId: 1,
    pointerType,
    isPrimary: true,
    button: 0,
  } as PointerEvent;
}

function createCalendar(contentItems: ContentResponse[] = []) {
  const httpTesting = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(CalendarPage);
  fixture.detectChanges();
  httpTesting.expectOne('/api/v1/settings').flush({
    timezone: 'Europe/Madrid',
    defaultReminderMinutesBefore: 10,
    defaultAssignmentCutoffMinutesBefore: 30,
    recurrenceHorizonDays: 30,
  });
  httpTesting.expectOne((request) => request.url === '/api/v1/content').flush({ items: contentItems });
  fixture.detectChanges();
  const initialRange = httpTesting.expectOne((request) => request.url === '/api/v1/sessions');
  initialRange.flush({ items: [plannedSession] });
  fixture.detectChanges();
  return fixture;
}

function eventFor(session: SessionResponse) {
  return {
    id: session.id,
    title: session.content?.title ?? 'Unassigned session',
    start: session.startsAt,
    end: session.endsAt,
    classNames: [
      `session-${session.status.toLowerCase()}`,
      ...(!session.content ? ['session-unassigned'] : []),
    ],
  };
}

const plannedSession: SessionResponse = {
  id: '7b3b7a3e-5cf4-4f80-ae44-8c83c7a3f55e',
  seriesId: null,
  startsAt: '2026-10-26T09:00:00Z',
  endsAt: '2026-10-26T10:15:00Z',
  timezone: 'Europe/Madrid',
  content: { id: 'content-1', title: 'Listening lesson', url: 'https://example.org/lesson', type: 'ARTICLE' },
  status: 'PLANNED',
  reminderMinutesBefore: 10,
  assignmentCutoffMinutesBefore: 30,
  completedAt: null,
};

const pendingItem: ContentResponse = {
  id: 'content-2',
  url: 'https://example.org/next-lesson',
  normalizedUrl: 'https://example.org/next-lesson',
  title: 'Next lesson',
  type: 'ARTICLE',
  thumbnailUrl: null,
  sourceName: 'Example',
  estimatedDurationMinutes: null,
  status: 'PENDING',
  assignedSessionId: null,
  createdAt: '2026-10-23T10:00:00Z',
  completedAt: null,
  durationSeconds: null
};

const assignedItem: ContentResponse = {
  ...pendingItem,
  id: 'content-1',
  url: plannedSession.content!.url,
  normalizedUrl: plannedSession.content!.url,
  title: plannedSession.content!.title,
  assignedSessionId: plannedSession.id,
};

const seriesDetails = {
  id: 'series-1',
  startDate: '2026-10-26',
  endDate: null,
  startTime: '09:30:00',
  durationMinutes: 45,
  timezone: 'Europe/Madrid',
  daysOfWeek: ['MONDAY', 'WEDNESDAY'],
  reminderMinutesBefore: 10,
  assignmentCutoffMinutesBefore: 30,
  active: true,
  generatedThrough: '2027-01-21',
};