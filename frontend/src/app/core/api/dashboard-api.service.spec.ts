import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DashboardApiService } from './dashboard-api.service';

describe('DashboardApiService', () => {
  let service: DashboardApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the dashboard summary from the documented endpoint', () => {
    const response = {
      pendingContentCount: 12,
      completedContentCount: 34,
      todaySessionCount: 2,
      unassignedUpcomingSessionCount: 1,
      nextSession: {
        id: '7b3b7a3e-5cf4-4f80-ae44-8c83c7a3f55e',
        startsAt: '2026-10-26T06:30:00Z',
        title: 'Example video title',
      },
    };
    let actual = null;
    service.getDashboard().subscribe((summary) => actual = summary);

    const request = httpTesting.expectOne('/api/v1/dashboard');
    expect(request.request.method).toBe('GET');
    request.flush(response);

    expect(actual).toEqual(response);
  });
});