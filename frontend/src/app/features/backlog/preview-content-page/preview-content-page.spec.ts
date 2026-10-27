import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ContentApiService } from '../../../core/api/content-api.service';
import { ContentResponse } from '../../../core/api/content';
import { SessionApiService } from '../../../core/api/session-api.service';
import { SessionResponse } from '../../../core/api/session';
import { PreviewContentPage } from './preview-content-page';

const sessionId = 'a51a8fbf-20dd-4bce-bcc8-44a4ed4ea290';
const contentId = '3e16a2f1-cf35-46f1-9d75-7ac12d31b81f';

describe('PreviewContentPage', () => {
  const content: ContentResponse = {
    id: contentId,
    url: 'https://example.org/lesson',
    normalizedUrl: 'https://example.org/lesson',
    title: 'A focused lesson',
    type: 'ARTICLE',
    thumbnailUrl: null,
    sourceName: 'example.org',
    durationSeconds: null,
    estimatedDurationMinutes: 12,
    status: 'PENDING',
    assignedSessionId: sessionId,
    createdAt: '2026-10-25T10:00:00Z',
    completedAt: null,
  };
  const session: SessionResponse = {
    id: sessionId,
    seriesId: null,
    startsAt: '2026-10-26T08:00:00Z',
    endsAt: '2026-10-26T08:30:00Z',
    timezone: 'Europe/Madrid',
    content: { id: contentId, title: content.title, url: content.url, type: content.type },
    status: 'PLANNED',
    reminderMinutesBefore: 5,
    assignmentCutoffMinutesBefore: 30,
    completedAt: null,
  };

  it('loads the notification session and only its assigned content', () => {
    const sessionApi = { getSessionById: vi.fn(() => of(session)) };
    const contentApi = { getContentById: vi.fn(() => of(content)) };
    const fixture = createFixture(sessionApi, contentApi, sessionId);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(sessionApi.getSessionById).toHaveBeenCalledWith(sessionId);
    expect(contentApi.getContentById).toHaveBeenCalledWith(contentId);
    expect(element.querySelector('#session-details-title')?.textContent).toContain(content.title);
    expect(element.textContent).toContain('30 minutes');
    expect(element.textContent).toContain('5 minutes before');
    expect(element.querySelector('.content-details h3')?.textContent).toContain(content.title);
    expect((element.querySelector('.open-link') as HTMLAnchorElement).href).toBe(content.url);
    expect(element.querySelector('a[href*="preview-content"]')).toBeNull();
  });

  it('shows session information without querying content when no content is assigned', () => {
    const unassignedSession = { ...session, content: null };
    const sessionApi = { getSessionById: vi.fn(() => of(unassignedSession)) };
    const contentApi = { getContentById: vi.fn() };
    const fixture = createFixture(sessionApi, contentApi, sessionId);
    fixture.detectChanges();

    expect(contentApi.getContentById).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No content is assigned');
  });

  it('rejects invalid session IDs without calling the APIs', () => {
    const sessionApi = { getSessionById: vi.fn() };
    const contentApi = { getContentById: vi.fn() };
    const fixture = createFixture(sessionApi, contentApi, '../settings');
    fixture.detectChanges();

    expect(sessionApi.getSessionById).not.toHaveBeenCalled();
    expect(contentApi.getContentById).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('valid session');
  });

  function createFixture(
    sessionApi: Pick<SessionApiService, 'getSessionById'>,
    contentApi: Pick<ContentApiService, 'getContentById'>,
    routeSessionId: string,
  ) {
    TestBed.configureTestingModule({
      imports: [PreviewContentPage],
      providers: [
        { provide: SessionApiService, useValue: sessionApi },
        { provide: ContentApiService, useValue: contentApi },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ sessionId: routeSessionId }) } },
        },
      ],
    });
    return TestBed.createComponent(PreviewContentPage);
  }
});
