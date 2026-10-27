import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SessionApiService } from './session-api.service';

describe('SessionApiService', () => {
  let service: SessionApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('creates a one-off session', () => {
    const request = {
      startsAt: '2026-10-26T07:15:00Z',
      durationMinutes: 30,
      timezone: 'Europe/Madrid',
      contentItemId: null,
      reminderMinutesBefore: 10,
      assignmentCutoffMinutesBefore: 30,
    };
    service.createSession(request).subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/sessions');
    expect(httpRequest.request.method).toBe('POST');
    expect(httpRequest.request.body).toEqual(request);
    httpRequest.flush({});
  });

  it('updates a session through the PATCH endpoint', () => {
    const request = {
      startsAt: '2026-10-27T08:15:00Z',
      durationMinutes: 45,
      contentItemId: 'content-1',
    };
    service.updateSession('session-1', request).subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/sessions/session-1');
    expect(httpRequest.request.method).toBe('PATCH');
    expect(httpRequest.request.body).toEqual(request);
    httpRequest.flush({});
  });

  it('completes a session through the atomic completion endpoint', () => {
    service.completeSession('session-1').subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/sessions/session-1/complete');
    expect(httpRequest.request.method).toBe('POST');
    expect(httpRequest.request.body).toEqual({});
    httpRequest.flush({});
  });

  it('skips a session through the skip endpoint', () => {
    service.skipSession('session-1').subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/sessions/session-1/skip');
    expect(httpRequest.request.method).toBe('POST');
    expect(httpRequest.request.body).toEqual({});
    httpRequest.flush({});
  });

  it('cancels a session through the cancel endpoint', () => {
    service.cancelSession('session-1').subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/sessions/session-1/cancel');
    expect(httpRequest.request.method).toBe('POST');
    expect(httpRequest.request.body).toEqual({});
    httpRequest.flush({});
  });

  it('gets one session by ID', () => {
    service.getSessionById('session-1').subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/sessions/session-1');
    expect(httpRequest.request.method).toBe('GET');
    httpRequest.flush({});
  });

  it('loads sessions by an exclusive instant range', () => {
    service.getSessions('2026-10-26T00:00:00Z', '2026-10-27T00:00:00Z').subscribe();

    const httpRequest = httpTesting.expectOne((request) => request.url === '/api/v1/sessions');
    expect(httpRequest.request.params.get('from')).toBe('2026-10-26T00:00:00Z');
    expect(httpRequest.request.params.get('to')).toBe('2026-10-27T00:00:00Z');
    httpRequest.flush({ items: [] });
  });
});