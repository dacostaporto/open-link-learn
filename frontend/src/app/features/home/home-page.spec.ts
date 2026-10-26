import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { HomePage } from './home-page';

describe('HomePage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('shows dashboard metrics, local session time, and relevant links', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Loading your dashboard');
    flushSettings(fixture);
    httpTesting.expectOne('/api/v1/dashboard').flush(summary);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('12');
    expect(element.textContent).toContain('34');
    expect(element.textContent).toContain('2');
    expect(element.textContent).toContain('7:30 AM');
    expect(element.textContent).toContain('Europe/Madrid');
    expect(element.textContent).toContain('1 upcoming session has no learning item assigned.');
    expect(element.querySelector('a[href="/backlog"]')).not.toBeNull();
    expect(element.querySelector('a[href="/history"]')).not.toBeNull();
    expect(element.querySelector('a[href^="/calendar?sessionId="]')).not.toBeNull();
  });

  it('saves a link from the home banner and refreshes dashboard metrics', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    flushSettings(fixture);
    httpTesting.expectOne('/api/v1/dashboard').flush(summary);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const urlInput = element.querySelector('#content-url') as HTMLInputElement;
    urlInput.value = 'https://example.org/idea';
    urlInput.dispatchEvent(new Event('input'));
    (element.querySelector('button[type="submit"]') as HTMLButtonElement).click();

    const createRequest = httpTesting.expectOne('/api/v1/content');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual({
      url: 'https://example.org/idea',
      title: null,
    });
    createRequest.flush({
      id: 'content-1',
      url: 'https://example.org/idea',
      normalizedUrl: 'https://example.org/idea',
      title: 'idea',
      type: 'ARTICLE',
      thumbnailUrl: null,
      sourceName: 'example.org',
      durationSeconds: null,
      estimatedDurationMinutes: null,
      status: 'PENDING',
      assignedSessionId: null,
      createdAt: '2026-10-26T10:00:00Z',
      completedAt: null,
    });
    fixture.detectChanges();

    expect(element.querySelector('[role="status"]')?.textContent).toContain('Added idea to your backlog.');
    httpTesting.expectOne('/api/v1/dashboard').flush({ ...summary, pendingContentCount: 13 });
    fixture.detectChanges();
    expect(element.textContent).toContain('13');
  });

  it('shows an empty state and calendar link when no sessions are scheduled', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    flushSettings(fixture);
    httpTesting.expectOne('/api/v1/dashboard').flush({
      pendingContentCount: 0,
      completedContentCount: 0,
      todaySessionCount: 0,
      unassignedUpcomingSessionCount: 0,
      nextSession: null,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Nothing scheduled yet.');
    expect(element.textContent).toContain('Your planner is clear.');
    expect(element.querySelector('a[href="/calendar"]')).not.toBeNull();
    expect(element.querySelector('.assignment-warning')).toBeNull();
  });

  it('preserves dashboard data and labels UTC when settings cannot load', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/settings').flush({}, { status: 503, statusText: 'Unavailable' });
    httpTesting.expectOne('/api/v1/dashboard').flush(summary);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('12');
    expect(element.textContent).toContain('UTC');
    expect(element.textContent).toContain('timezone settings could not be loaded');
  });

  it('labels UTC while settings are pending, then formats with the configured timezone', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/dashboard').flush(summary);
    fixture.detectChanges();

    let element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('temporarily shown in UTC');
    expect(element.querySelector('.timezone-label')?.textContent).toContain('UTC');
    expect(element.textContent).toContain('6:30 AM');

    httpTesting.expectOne('/api/v1/settings').flush({
      timezone: 'Europe/Madrid',
      defaultReminderMinutesBefore: 10,
      defaultAssignmentCutoffMinutesBefore: 30,
      recurrenceHorizonDays: 90,
    });
    fixture.detectChanges();

    element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Europe/Madrid');
    expect(element.textContent).toContain('7:30 AM');
    expect(element.textContent).not.toContain('temporarily shown in UTC');
  });

  it('shows an API error and recovers through retry', () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    flushSettings(fixture);
    httpTesting.expectOne('/api/v1/dashboard').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Dashboard data could not be loaded.');
    (element.querySelector('.dashboard-error button') as HTMLButtonElement).click();
    httpTesting.expectOne('/api/v1/dashboard').flush(summary);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Example session');
  });

  function flushSettings(fixture: ComponentFixture<HomePage>): void {
    httpTesting.expectOne('/api/v1/settings').flush({
      timezone: 'Europe/Madrid',
      defaultReminderMinutesBefore: 10,
      defaultAssignmentCutoffMinutesBefore: 30,
      recurrenceHorizonDays: 90,
    });
    fixture.detectChanges();
  }
});

const summary = {
  pendingContentCount: 12,
  completedContentCount: 34,
  todaySessionCount: 2,
  unassignedUpcomingSessionCount: 1,
  nextSession: {
    id: '7b3b7a3e-5cf4-4f80-ae44-8c83c7a3f55e',
    startsAt: '2026-10-26T06:30:00Z',
    title: 'Example session',
  },
};