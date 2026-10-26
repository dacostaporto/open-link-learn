import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SessionSeriesApiService } from './session-series-api.service';
import { Weekday } from './session-series';

describe('SessionSeriesApiService', () => {
  let service: SessionSeriesApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionSeriesApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('creates a weekly session series with the typed request body', () => {
    const request = {
      startDate: '2026-10-26',
      endDate: null,
      startTime: '09:30',
      durationMinutes: 45,
      timezone: 'Europe/Madrid',
      daysOfWeek: ['MONDAY', 'WEDNESDAY'] as Weekday[],
      reminderMinutesBefore: 10,
      assignmentCutoffMinutesBefore: 30,
    };
    service.createSeries(request).subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/session-series');
    expect(httpRequest.request.method).toBe('POST');
    expect(httpRequest.request.body).toEqual(request);
    httpRequest.flush({ ...request, id: 'series-1', active: true, generatedThrough: '2027-01-24' });
  });

  it('reads the saved recurrence rule by series identity', () => {
    service.getSeries('series-1').subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/session-series/series-1');
    expect(httpRequest.request.method).toBe('GET');
    httpRequest.flush({});
  });

  it('updates only with the supported entire-series scope', () => {
    const request = {
      applyTo: 'ENTIRE_SERIES' as const,
      startDate: '2026-10-26',
      endDate: null,
      startTime: '09:30',
      durationMinutes: 45,
      timezone: 'Europe/Madrid',
      daysOfWeek: ['MONDAY'] as Weekday[],
      reminderMinutesBefore: 10,
      assignmentCutoffMinutesBefore: 30,
    };
    service.updateSeries('series-1', request).subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/session-series/series-1');
    expect(httpRequest.request.method).toBe('PATCH');
    expect(httpRequest.request.body).toEqual(request);
    httpRequest.flush({ ...request, id: 'series-1', active: true, generatedThrough: '2027-01-24' });
  });

  it('cancels a series through its dedicated endpoint', () => {
    service.cancelSeries('series-1').subscribe();

    const httpRequest = httpTesting.expectOne('/api/v1/session-series/series-1/cancel');
    expect(httpRequest.request.method).toBe('POST');
    expect(httpRequest.request.body).toEqual({});
    httpRequest.flush(null);
  });
});
