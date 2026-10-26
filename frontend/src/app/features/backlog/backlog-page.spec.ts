import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BacklogPage } from './backlog-page';

describe('BacklogPage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BacklogPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads and renders pending content with safe open links', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();

    const request = httpTesting.expectOne((req) => req.url === '/api/v1/content');
    expect(request.request.params.get('status')).toBe('PENDING');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    request.flush({
      items: [contentItem],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h3')?.textContent).toContain('Listening lesson');
    expect(element.querySelector('.type-marker')?.textContent).toContain('VIDEO');
    const thumbnail = element.querySelector('.thumbnail-frame img') as HTMLImageElement;
    expect(thumbnail.src).toBe(contentItem.thumbnailUrl);
    expect(thumbnail.alt).toBe('Thumbnail for Listening lesson');
    expect(element.querySelector('.content-details p')?.textContent).toContain('1:35');
    const openLink = element.querySelector('.open-link') as HTMLAnchorElement;
    expect(openLink.href).toBe(contentItem.url);
    expect(openLink.rel).toContain('noopener');
  });

  it('keeps the video label when no thumbnail is available', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [{ ...contentItem, thumbnailUrl: null }],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.thumbnail-frame')).toBeNull();
    expect(element.querySelector('.type-marker')?.textContent).toContain('VIDEO');
  });

  it('shows an empty state when the pending list is empty', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Your backlog is clear');
  });

  it('reloads the first page after content is added', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const urlInput = element.querySelector('#content-url') as HTMLInputElement;
    urlInput.value = contentItem.url;
    urlInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (element.querySelector('button[type="submit"]') as HTMLButtonElement).click();

    httpTesting.expectOne('/api/v1/content').flush(contentItem);
    const refresh = httpTesting.expectOne((req) => req.url === '/api/v1/content');
    expect(refresh.request.params.get('status')).toBe('PENDING');
    expect(refresh.request.params.get('page')).toBe('0');
    refresh.flush({
      items: [contentItem],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    expect(element.querySelector('h3')?.textContent).toContain('Listening lesson');
  });

  it('updates the displayed title and duration after a successful edit', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [contentItem],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    (element.querySelector('[aria-label^="Edit details"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const titleInput = element.querySelector('.title-editor input') as HTMLInputElement;
    titleInput.value = 'Updated practice clip';
    titleInput.dispatchEvent(new Event('input'));
    const durationInput = element.querySelector('input[type="number"]') as HTMLInputElement;
    durationInput.value = '18';
    durationInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (element.querySelector('.save-edit-button') as HTMLButtonElement).click();

    const request = httpTesting.expectOne(`/api/v1/content/${contentItem.id}`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ title: 'Updated practice clip', estimatedDurationMinutes: 18 });
    request.flush({ ...contentItem, title: 'Updated practice clip', estimatedDurationMinutes: 18 });
    fixture.detectChanges();

    expect(element.querySelector('h3')?.textContent).toContain('Updated practice clip');
    (element.querySelector('[aria-label^="Edit details"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect((element.querySelector('input[type="number"]') as HTMLInputElement).value).toBe('18');
  });

  it('rejects fractional duration values before sending an update', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [contentItem],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    (element.querySelector('[aria-label^="Edit details"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const durationInput = element.querySelector('input[type="number"]') as HTMLInputElement;
    durationInput.value = '2.5';
    durationInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (element.querySelector('.save-edit-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.textContent).toContain('whole number of minutes greater than zero');
    expect(durationInput.getAttribute('aria-invalid')).toBe('true');
    expect(durationInput.getAttribute('aria-describedby')).toBe('content-duration-error');
    httpTesting.expectNone(`/api/v1/content/${contentItem.id}`);
  });

  it('associates a missing title error with the title input', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [contentItem],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    (element.querySelector('[aria-label^="Edit details"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const titleInput = element.querySelector('.title-editor input') as HTMLInputElement;
    titleInput.value = '';
    titleInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (element.querySelector('.save-edit-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(titleInput.getAttribute('aria-invalid')).toBe('true');
    expect(titleInput.getAttribute('aria-describedby')).toBe('content-title-error');
    expect(element.querySelector('#content-title-error')?.textContent).toContain('Enter a title.');
    httpTesting.expectNone(`/api/v1/content/${contentItem.id}`);
  });

  it('clears an existing estimate by sending null', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [{ ...contentItem, estimatedDurationMinutes: 12 }],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    (element.querySelector('[aria-label^="Edit details"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const durationInput = element.querySelector('input[type="number"]') as HTMLInputElement;
    durationInput.value = '';
    durationInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (element.querySelector('.save-edit-button') as HTMLButtonElement).click();

    const request = httpTesting.expectOne(`/api/v1/content/${contentItem.id}`);
    expect(request.request.body).toEqual({ title: contentItem.title, estimatedDurationMinutes: null });
    request.flush({ ...contentItem, estimatedDurationMinutes: null });
  });

  it('displays a useful API failure while keeping the editor open', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [contentItem],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    (element.querySelector('[aria-label^="Edit details"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const titleInput = element.querySelector('.title-editor input') as HTMLInputElement;
    titleInput.value = 'Updated title';
    titleInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (element.querySelector('.save-edit-button') as HTMLButtonElement).click();
    httpTesting.expectOne(`/api/v1/content/${contentItem.id}`).flush(
      { detail: 'Content details could not be saved.' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Content details could not be saved.');
    expect(element.querySelector('.title-editor')).not.toBeNull();
  });

  it('removes an item from the backlog after completion', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [contentItem],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const completeButton = [...element.querySelectorAll('button')]
      .find((button) => button.textContent?.trim() === 'Complete') as HTMLButtonElement;
    completeButton.click();
    fixture.detectChanges();
    expect(completeButton.disabled).toBe(true);

    const request = httpTesting.expectOne(`/api/v1/content/${contentItem.id}/complete`);
    expect(request.request.method).toBe('POST');
    request.flush({ ...contentItem, status: 'COMPLETED', completedAt: '2026-10-23T11:00:00Z' });
    fixture.detectChanges();

    expect(element.querySelector('.content-row')).toBeNull();
    expect(element.textContent).toContain('Your backlog is clear');
  });

  it('keeps content in the backlog and reports an error when completion fails', () => {
    const fixture = TestBed.createComponent(BacklogPage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/content').flush({
      items: [contentItem],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const completeButton = element.querySelector('[aria-label="Mark Listening lesson as complete"]') as HTMLButtonElement;
    completeButton.click();
    fixture.detectChanges();

    httpTesting.expectOne(`/api/v1/content/${contentItem.id}/complete`).flush(
      { detail: 'Completion could not be saved.' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.detectChanges();

    expect(element.querySelector('.content-row')).not.toBeNull();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('This item could not be completed. Try again.');
    expect(completeButton.disabled).toBe(false);
  });
});

const contentItem = {
  id: 'content-1',
  url: 'https://www.youtube.com/watch?v=lesson-1',
  normalizedUrl: 'https://www.youtube.com/watch?v=lesson-1',
  title: 'Listening lesson',
  type: 'YOUTUBE',
  thumbnailUrl: 'https://img.youtube.com/vi/lesson-1/hqdefault.jpg',
  sourceName: 'YouTube',
  durationSeconds: 95,
  estimatedDurationMinutes: null,
  status: 'PENDING',
  assignedSessionId: null,
  createdAt: '2026-10-23T10:00:00Z',
  completedAt: null,
};