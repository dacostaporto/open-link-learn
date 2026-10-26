import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AddContentPage } from './add-content-page';

describe('AddContentPage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddContentPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('submits valid content once and displays success', () => {
    const fixture = TestBed.createComponent(AddContentPage);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const urlInput = element.querySelector('#content-url') as HTMLInputElement;
    const titleInput = element.querySelector('#content-title') as HTMLInputElement;
    urlInput.value = 'https://www.youtube.com/watch?v=lesson-1';
    urlInput.dispatchEvent(new Event('input'));
    titleInput.value = 'Listening lesson';
    titleInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const submitButton = element.querySelector('button[type="submit"]') as HTMLButtonElement;
    submitButton.click();
    fixture.detectChanges();
    expect(submitButton.disabled).toBe(true);
    expect(submitButton.textContent).toContain('Saving...');

    const request = httpTesting.expectOne('/api/v1/content');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      url: 'https://www.youtube.com/watch?v=lesson-1',
      title: 'Listening lesson',
    });
    request.flush({
      id: 'content-1',
      url: 'https://www.youtube.com/watch?v=lesson-1',
      normalizedUrl: 'https://www.youtube.com/watch?v=lesson-1',
      title: 'Listening lesson',
      type: 'YOUTUBE',
      thumbnailUrl: null,
      sourceName: 'YouTube',
      estimatedDurationMinutes: null,
      status: 'PENDING',
      assignedSessionId: null,
      createdAt: '2026-10-23T10:00:00Z',
      completedAt: null,
    });
    fixture.detectChanges();

    expect(element.querySelector('[role="status"]')?.textContent).toContain('Listening lesson');
    expect(submitButton.disabled).toBe(false);
    expect(urlInput.value).toBe('');
  });

  it('includes an explicitly selected content type override', () => {
    const fixture = TestBed.createComponent(AddContentPage);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const urlInput = element.querySelector('#content-url') as HTMLInputElement;
    urlInput.value = 'https://example.org/lesson';
    urlInput.dispatchEvent(new Event('input'));
    const typeSelect = element.querySelector('#content-kind') as HTMLSelectElement;
    typeSelect.value = 'YOUTUBE';
    typeSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (element.querySelector('button[type="submit"]') as HTMLButtonElement).click();

    const request = httpTesting.expectOne('/api/v1/content');
    expect(request.request.body).toEqual({
      url: 'https://example.org/lesson',
      title: null,
      type: 'YOUTUBE',
    });
    request.flush({
      id: 'content-2',
      url: 'https://example.org/lesson',
      normalizedUrl: 'https://example.org/lesson',
      title: 'lesson',
      type: 'YOUTUBE',
      thumbnailUrl: null,
      sourceName: 'YouTube',
      estimatedDurationMinutes: null,
      status: 'PENDING',
      assignedSessionId: null,
      createdAt: '2026-10-23T10:00:00Z',
      completedAt: null,
    });
  });

  it('rejects a non-http URL before making a request', () => {
    const fixture = TestBed.createComponent(AddContentPage);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const urlInput = element.querySelector('#content-url') as HTMLInputElement;
    urlInput.value = 'ftp://example.org/lesson';
    urlInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (element.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.querySelector('#url-error')?.textContent).toContain('valid HTTP or HTTPS URL');
    httpTesting.expectNone('/api/v1/content');
  });

  it('shows a useful duplicate response from the API', () => {
    const fixture = TestBed.createComponent(AddContentPage);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const urlInput = element.querySelector('#content-url') as HTMLInputElement;
    urlInput.value = 'https://example.org/lesson';
    urlInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (element.querySelector('button[type="submit"]') as HTMLButtonElement).click();

    httpTesting.expectOne('/api/v1/content').flush(
      { detail: 'This URL is already in your backlog.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('This URL is already in your backlog.');
  });
});