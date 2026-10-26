import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SessionSeriesEditPanel } from './session-series-edit-panel';
import { SessionSeriesResponse } from '../../../core/api/session-series';

describe('SessionSeriesEditPanel', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionSeriesEditPanel],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the saved rule and submits the supported entire-series update', () => {
    const fixture = createLoadedEditor();
    const element = fixture.nativeElement as HTMLElement;
    expect((element.querySelector('#edit-series-start') as HTMLInputElement).value).toBe('2026-10-26');
    expect((element.querySelector('#edit-series-duration') as HTMLInputElement).value).toBe('45');
    expect((element.querySelector('#edit-weekday-MONDAY') as HTMLInputElement).checked).toBe(true);
    expect((element.querySelector('#edit-weekday-WEDNESDAY') as HTMLInputElement).checked).toBe(true);
    expect(element.textContent).toContain('Changes apply to the entire series.');

    setInput(element, '#edit-series-time', '10:15');
    const wednesday = element.querySelector<HTMLInputElement>('#edit-weekday-WEDNESDAY')!;
    wednesday.checked = false;
    wednesday.dispatchEvent(new Event('change', { bubbles: true }));
    const friday = element.querySelector<HTMLInputElement>('#edit-weekday-FRIDAY')!;
    friday.checked = true;
    friday.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();

    let wasSaved = false;
    fixture.componentInstance.saved.subscribe(() => wasSaved = true);
    (element.querySelector('.submit-button') as HTMLButtonElement).click();

    const update = httpTesting.expectOne('/api/v1/session-series/series-1');
    expect(update.request.method).toBe('PATCH');
    expect(update.request.body).toEqual({
      applyTo: 'ENTIRE_SERIES',
      startDate: '2026-10-26',
      endDate: null,
      startTime: '10:15',
      durationMinutes: 45,
      timezone: 'Europe/Madrid',
      daysOfWeek: ['MONDAY', 'FRIDAY'],
      reminderMinutesBefore: 10,
      assignmentCutoffMinutesBefore: 30,
    });
    update.flush({ ...series, startTime: '10:15:00', daysOfWeek: ['MONDAY', 'FRIDAY'] });
    fixture.detectChanges();
    expect(wasSaved).toBe(true);
  });

  it('keeps edits visible and displays the API error when an update fails', () => {
    const fixture = createLoadedEditor();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#edit-series-duration', '60');
    fixture.detectChanges();
    (element.querySelector('.submit-button') as HTMLButtonElement).click();

    httpTesting.expectOne('/api/v1/session-series/series-1').flush(
      { detail: 'The series schedule could not be changed.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent)
      .toContain('The series schedule could not be changed.');
    expect((element.querySelector('#edit-series-duration') as HTMLInputElement).value).toBe('60');
  });

  it('associates invalid duration feedback with the duration input', () => {
    const fixture = createLoadedEditor();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#edit-series-duration', '0');
    fixture.detectChanges();
    (element.querySelector('.submit-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    const duration = element.querySelector('#edit-series-duration') as HTMLInputElement;
    expect(duration.getAttribute('aria-invalid')).toBe('true');
    expect(duration.getAttribute('aria-describedby')).toBe('edit-series-duration-error');
    expect(element.querySelector('#edit-series-duration-error')?.textContent)
      .toContain('Use a whole number from 1 to 720.');
    httpTesting.expectNone('/api/v1/session-series/series-1');
  });

  function createLoadedEditor() {
    const fixture = TestBed.createComponent(SessionSeriesEditPanel);
    fixture.componentRef.setInput('seriesId', 'series-1');
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/session-series/series-1').flush(series);
    fixture.detectChanges();
    return fixture;
  }

  function setInput(element: HTMLElement, selector: string, value: string): void {
    const input = element.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
});

const series: SessionSeriesResponse = {
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
