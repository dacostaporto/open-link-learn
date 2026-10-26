import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ArchivePage } from './archive-page';

describe('ArchivePage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArchivePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads completed items and displays their completion date', () => {
    const fixture = TestBed.createComponent(ArchivePage);
    fixture.detectChanges();

    const request = httpTesting.expectOne((req) => req.url === '/api/v1/history');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    request.flush({
      items: [completedItem],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h3')?.textContent).toContain('Completed lesson');
    expect(element.querySelector('.content-details p')?.textContent).toContain('Completed Oct 23, 2026');
    expect((element.querySelector('.open-link') as HTMLAnchorElement).rel).toContain('noopener');
  });

  it('shows an empty archive state', () => {
    const fixture = TestBed.createComponent(ArchivePage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/history').flush({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nothing completed yet');
  });

  it('paginates and searches the archive from the first page', () => {
    const fixture = TestBed.createComponent(ArchivePage);
    fixture.detectChanges();
    httpTesting.expectOne((req) => req.url === '/api/v1/history').flush({
      items: [completedItem],
      page: 0,
      size: 20,
      totalItems: 21,
      totalPages: 2,
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    (element.querySelector('.pagination button:last-child') as HTMLButtonElement).click();
    const nextPage = httpTesting.expectOne((req) => req.url === '/api/v1/history');
    expect(nextPage.request.params.get('page')).toBe('1');
    expect(nextPage.request.params.get('size')).toBe('20');
    nextPage.flush({ items: [completedItem], page: 1, size: 20, totalItems: 21, totalPages: 2 });
    fixture.detectChanges();

    const searchInput = element.querySelector('#archive-search') as HTMLInputElement;
    searchInput.value = 'Example';
    searchInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (element.querySelector('.search-button') as HTMLButtonElement).click();
    const searchRequest = httpTesting.expectOne((req) => req.url === '/api/v1/history');
    expect(searchRequest.request.params.get('search')).toBe('Example');
    expect(searchRequest.request.params.get('page')).toBe('0');
    searchRequest.flush({ items: [completedItem], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    fixture.detectChanges();

    expect(element.querySelector('.pagination')).toBeNull();
    expect(element.querySelector('h3')?.textContent).toContain('Completed lesson');
  });
});

const completedItem = {
  id: 'content-2',
  url: 'https://example.org/lesson',
  normalizedUrl: 'https://example.org/lesson',
  title: 'Completed lesson',
  type: 'ARTICLE',
  thumbnailUrl: null,
  sourceName: 'Example',
  estimatedDurationMinutes: null,
  status: 'COMPLETED',
  assignedSessionId: null,
  createdAt: '2026-10-22T10:00:00Z',
  completedAt: '2026-10-23T11:00:00Z',
};