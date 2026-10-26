import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionSeriesCreatePage } from './session-series-create-page';

describe('SessionSeriesCreatePage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionSeriesCreatePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('uses planner defaults and blocks submission without a selected weekday', () => {
    const fixture = createLoadedPage();
    const element = fixture.nativeElement as HTMLElement;
    expect((element.querySelector('#series-timezone') as HTMLInputElement).value).toBe(settings.timezone);
    expect((element.querySelector('#series-reminder') as HTMLInputElement).value).toBe('10');
    expect((element.querySelector('#series-cutoff') as HTMLInputElement).value).toBe('30');

    const selectedDay = element.querySelector<HTMLInputElement>('.weekday-option input:checked')!;
    selectedDay.checked = false;
    selectedDay.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    (element.querySelector('.submit-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.textContent).toContain('Choose at least one day.');
    httpTesting.expectNone('/api/v1/session-series');
  });

  it('maps recurrence fields and profile defaults into the create request', () => {
    const fixture = createLoadedPage();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#series-start-date', '2026-10-26');
    setInput(element, '#series-start-time', '09:30');
    setInput(element, '#series-duration', '45');
    setInput(element, '#series-timezone', 'Europe/London');
    setInput(element, '#series-reminder', '15');
    setInput(element, '#series-cutoff', '60');

    for (const checkbox of element.querySelectorAll<HTMLInputElement>('.weekday-option input')) {
      checkbox.checked = false;
      checkbox.dispatchEvent(new Event('change', { bubbles: true }));
    }
    for (const weekday of ['MONDAY', 'WEDNESDAY']) {
      const checkbox = element.querySelector<HTMLInputElement>(`#weekday-${weekday}`)!;
      checkbox.checked = true;
      checkbox.dispatchEvent(new Event('change', { bubbles: true }));
    }
    fixture.detectChanges();
    (element.querySelector('.submit-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    const submitButton = element.querySelector('.submit-button') as HTMLButtonElement;
    expect(submitButton.disabled).toBe(true);
    expect(submitButton.textContent).toContain('Creating series...');
    (element.querySelector('.submit-button') as HTMLButtonElement).click();

    const request = httpTesting.expectOne('/api/v1/session-series');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      startDate: '2026-10-26',
      endDate: null,
      startTime: '09:30',
      durationMinutes: 45,
      timezone: 'Europe/London',
      daysOfWeek: ['MONDAY', 'WEDNESDAY'],
      reminderMinutesBefore: 15,
      assignmentCutoffMinutesBefore: 60,
    });
    request.flush({ ...request.request.body, id: 'series-1', active: true, generatedThrough: '2027-01-24' });
    fixture.detectChanges();
    expect(element.textContent).toContain('Occurrences have been generated through 2027-01-24.');
    expect(element.querySelector('.created-state a')?.textContent).toContain('View calendar');
    expect(element.querySelector('.series-form')).toBeNull();
  });

  it('shows server validation details without discarding entered values', () => {
    const fixture = createLoadedPage();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#series-end-date', '2026-12-31');
    fixture.detectChanges();
    (element.querySelector('.submit-button') as HTMLButtonElement).click();
    httpTesting.expectOne('/api/v1/session-series').flush(
      { detail: 'The series end date is outside the allowed range.' },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent)
      .toContain('The series end date is outside the allowed range.');
    expect((element.querySelector('#series-end-date') as HTMLInputElement).value).toBe('2026-12-31');
  });

  it('rejects an end date before the start date', () => {
    const fixture = createLoadedPage();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#series-start-date', '2026-10-26');
    setInput(element, '#series-end-date', '2026-10-25');
    fixture.detectChanges();
    (element.querySelector('.submit-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.textContent).toContain('End date must be on or after the start date.');
    const endDate = element.querySelector('#series-end-date') as HTMLInputElement;
    expect(endDate.getAttribute('aria-invalid')).toBe('true');
    expect(endDate.getAttribute('aria-describedby')).toBe('series-end-date-error');
    httpTesting.expectNone('/api/v1/session-series');
  });

  function createLoadedPage() {
    const fixture = TestBed.createComponent(SessionSeriesCreatePage);
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/settings').flush(settings);
    fixture.detectChanges();
    return fixture;
  }

  function setInput(element: HTMLElement, selector: string, value: string): void {
    const input = element.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
});

const settings = {
  timezone: 'Europe/Madrid',
  defaultReminderMinutesBefore: 10,
  defaultAssignmentCutoffMinutesBefore: 30,
  recurrenceHorizonDays: 90,
};
