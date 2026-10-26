import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SettingsPage } from './settings-page';

describe('SettingsPage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads persisted settings and explains defaults-only and unsupported push behavior', () => {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Loading your settings');
    httpTesting.expectOne('/api/v1/settings').flush(settings);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect((element.querySelector('#timezone') as HTMLInputElement).value).toBe(settings.timezone);
    expect((element.querySelector('#reminder-lead') as HTMLInputElement).value).toBe('10');
    expect((element.querySelector('#assignment-cutoff') as HTMLInputElement).value).toBe('30');
    expect((element.querySelector('#recurrence-horizon') as HTMLInputElement).value).toBe('90');
    expect(element.textContent).toContain('Existing sessions and series will not be changed.');
    expect(element.textContent).toContain('Push needs a supported browser');
    expect(element.textContent).toContain('Not available');
  });

  it('validates timezone and numeric bounds before sending a patch', () => {
    const fixture = createLoadedPage();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#timezone', 'Not/AZone');
    setInput(element, '#reminder-lead', '1441');
    fixture.detectChanges();
    (element.querySelector('.save-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.textContent).toContain('Enter a valid IANA timezone.');
    expect(element.textContent).toContain('Enter a whole number from 0 to 1440.');
    httpTesting.expectNone((request) => request.method === 'PATCH');
  });

  it('saves all defaults with PATCH and shows a persisted success state', () => {
    const fixture = createLoadedPage();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#timezone', ' Europe/London ');
    setInput(element, '#reminder-lead', '15');
    setInput(element, '#assignment-cutoff', '60');
    setInput(element, '#recurrence-horizon', '120');
    fixture.detectChanges();
    (element.querySelector('.save-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    const request = httpTesting.expectOne('/api/v1/settings');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({
      timezone: 'Europe/London',
      defaultReminderMinutesBefore: 15,
      defaultAssignmentCutoffMinutesBefore: 60,
      recurrenceHorizonDays: 120,
    });
    expect(element.textContent).toContain('Saving...');
    request.flush({ ...settings, timezone: 'Europe/London', defaultReminderMinutesBefore: 15,
      defaultAssignmentCutoffMinutesBefore: 60, recurrenceHorizonDays: 120 });
    fixture.detectChanges();

    expect(element.querySelector('.success-message')?.textContent).toContain('Settings saved.');
    expect((element.querySelector('#timezone') as HTMLInputElement).value).toBe('Europe/London');

    const reloadedFixture = TestBed.createComponent(SettingsPage);
    reloadedFixture.detectChanges();
    httpTesting.expectOne('/api/v1/settings').flush({
      ...settings,
      timezone: 'Europe/London',
      defaultReminderMinutesBefore: 15,
      defaultAssignmentCutoffMinutesBefore: 60,
      recurrenceHorizonDays: 120,
    });
    reloadedFixture.detectChanges();
    expect((reloadedFixture.nativeElement.querySelector('#recurrence-horizon') as HTMLInputElement).value).toBe('120');
  });

  it('keeps edits visible and displays an API error when saving fails', () => {
    const fixture = createLoadedPage();
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, '#reminder-lead', '25');
    fixture.detectChanges();
    (element.querySelector('.save-button') as HTMLButtonElement).click();
    httpTesting.expectOne('/api/v1/settings').flush(
      { detail: 'The settings update was rejected.' },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('The settings update was rejected.');
    expect((element.querySelector('#reminder-lead') as HTMLInputElement).value).toBe('25');
  });

  it('offers a retry when persisted settings cannot be loaded', () => {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/settings').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Settings could not be loaded.');
    (element.querySelector('button') as HTMLButtonElement).click();
    httpTesting.expectOne('/api/v1/settings').flush(settings);
    fixture.detectChanges();
    expect((element.querySelector('#timezone') as HTMLInputElement).value).toBe(settings.timezone);
  });

  function createLoadedPage() {
    const fixture = TestBed.createComponent(SettingsPage);
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