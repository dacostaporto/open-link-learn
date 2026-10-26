package com.openlinklearn.content.application;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.openlinklearn.content.application.dto.HistoryPageResponse;
import com.openlinklearn.content.application.dto.ContentPageResponse;
import com.openlinklearn.content.application.dto.ContentResponse;
import com.openlinklearn.content.application.dto.CreateContentRequest;
import com.openlinklearn.content.application.dto.UpdateContentRequest;
import com.openlinklearn.content.domain.ContentItem;
import com.openlinklearn.content.domain.ContentMetadata;
import com.openlinklearn.content.domain.ContentMetadataProvider;
import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.content.domain.ContentUrlValidator;
import com.openlinklearn.content.domain.DuplicateContentException;
import com.openlinklearn.content.domain.InvalidContentUrlException;
import com.openlinklearn.session.domain.SessionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class ContentServiceTest {

    private final ContentRepository contentRepository = mock(ContentRepository.class);
    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ContentService contentService = new ContentService(
            contentRepository,
            new ContentUrlValidator(),
            new ContentMetadataResolver(List.of()),
            sessionRepository
    );

    @Test
    void createsPendingYoutubeContentWithFallbackMetadata() {
        when(contentRepository.existsByOwnerIdAndNormalizedUrl(any(), any())).thenReturn(false);
        when(contentRepository.saveAndFlush(any(ContentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContentResponse response = contentService.createContent(
                new CreateContentRequest("https://youtu.be/video-123", null)
        );

        assertEquals("youtu.be", response.title());
        assertEquals(ContentType.YOUTUBE, response.type());
        assertEquals(ContentStatus.PENDING, response.status());
        assertEquals("YouTube", response.sourceName());
        assertEquals(null, response.thumbnailUrl());
        verify(contentRepository).saveAndFlush(any(ContentItem.class));
    }

    @Test
    void createsContentWithMetadataFromTheMatchingProvider() throws Exception {
        when(contentRepository.existsByOwnerIdAndNormalizedUrl(any(), any())).thenReturn(false);
        when(contentRepository.saveAndFlush(any(ContentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ContentMetadataProvider provider = mock(ContentMetadataProvider.class);
        when(provider.supports(ContentType.ARTICLE)).thenReturn(true);
        when(provider.fetch(any())).thenReturn(Optional.of(
                new ContentMetadata("Article title", "Publisher", "https://img.example/cover.jpg")
        ));
        ContentService service = new ContentService(
                contentRepository,
                new ContentUrlValidator(),
                new ContentMetadataResolver(List.of(provider)),
                sessionRepository
        );

        ContentResponse response = service.createContent(
                new CreateContentRequest("https://example.org/article", null)
        );

        assertEquals("Article title", response.title());
        assertEquals("Publisher", response.sourceName());
        assertEquals("https://img.example/cover.jpg", response.thumbnailUrl());
    }

    @Test
    void initializesEstimatedMinutesFromVideoDuration() throws Exception {
        when(contentRepository.existsByOwnerIdAndNormalizedUrl(any(), any())).thenReturn(false);
        when(contentRepository.saveAndFlush(any(ContentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ContentMetadataProvider provider = mock(ContentMetadataProvider.class);
        when(provider.supports(ContentType.YOUTUBE)).thenReturn(true);
        when(provider.fetch(any())).thenReturn(Optional.of(
                new ContentMetadata("Video title", "Channel", "https://i.ytimg.com/vi/video-1/hqdefault.jpg", 125)
        ));
        ContentService service = new ContentService(
                contentRepository,
                new ContentUrlValidator(),
                new ContentMetadataResolver(List.of(provider)),
                sessionRepository
        );

        ContentResponse response = service.createContent(
                new CreateContentRequest("https://youtu.be/video-1", null)
        );

        assertEquals(125, response.durationSeconds());
        assertEquals(3, response.estimatedDurationMinutes());
    }

    @Test
    void rejectsUnsafeUrlBeforeCallingMetadataProvider() throws Exception {
        ContentMetadataProvider provider = mock(ContentMetadataProvider.class);
        when(provider.supports(ContentType.ARTICLE)).thenReturn(true);
        ContentService service = new ContentService(
                contentRepository,
                new ContentUrlValidator(),
                new ContentMetadataResolver(List.of(provider)),
                sessionRepository
        );

        assertThrows(InvalidContentUrlException.class, () -> service.createContent(
                new CreateContentRequest("http://127.0.0.1/private", null)
        ));

        verify(provider, never()).fetch(any());
    }

        @Test
        void usesTheOwnerSelectedContentTypeOverride() {
                when(contentRepository.existsByOwnerIdAndNormalizedUrl(any(), any())).thenReturn(false);
                when(contentRepository.saveAndFlush(any(ContentItem.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ContentResponse response = contentService.createContent(
                                new CreateContentRequest("https://example.org/lesson", null, ContentType.YOUTUBE)
                );

                assertEquals(ContentType.YOUTUBE, response.type());
                assertEquals("YouTube", response.sourceName());
        }

    @Test
    void rejectsDuplicateNormalizedUrl() {
        when(contentRepository.existsByOwnerIdAndNormalizedUrl(any(), any())).thenReturn(true);

        assertThrows(DuplicateContentException.class, () -> contentService.createContent(
                new CreateContentRequest("HTTPS://Example.org:443/lesson?unit=4#section", null)
        ));
        verify(contentRepository).existsByOwnerIdAndNormalizedUrl(any(),
                eq("https://example.org/lesson?unit=4"));
    }

    @Test
    void translatesConcurrentUniqueConstraintViolationToConflict() {
        when(contentRepository.existsByOwnerIdAndNormalizedUrl(any(), any())).thenReturn(false);
        when(contentRepository.saveAndFlush(any(ContentItem.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DuplicateContentException.class, () -> contentService.createContent(
                new CreateContentRequest("https://example.org/", null)
        ));
    }

        @Test
        void returnsFilteredContentInNewestFirstPages() {
                ContentItem item = new ContentItem(
                                UUID.randomUUID(),
                                "https://example.org/lesson",
                                "https://example.org/lesson",
                                "English lesson",
                                ContentType.ARTICLE,
                                "example.org"
                );
                PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
                when(contentRepository.searchByOwner(any(), any(), any(), any(), any()))
                                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

                ContentPageResponse response = contentService.getContent(
                                ContentStatus.PENDING,
                                ContentType.ARTICLE,
                                "lesson",
                                0,
                                20
                );

                assertEquals(1, response.items().size());
                assertEquals("English lesson", response.items().get(0).title());
                assertEquals(0, response.page());
                assertEquals(1, response.totalItems());
                assertEquals(1, response.totalPages());
                verify(contentRepository).searchByOwner(any(), any(), any(), any(), any());
        }

        @Test
        void returnsOwnerScopedCompletedHistoryWithStableCompletionOrderingAndSearch() {
                UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
                Instant completedAt = Instant.parse("2026-10-22T14:25:00Z");
                ContentItem item = new ContentItem(
                                ownerId,
                                "https://example.org/lesson",
                                "https://example.org/lesson",
                                "Example lesson",
                                ContentType.ARTICLE,
                                "Example source"
                );
                item.markCompleted(completedAt);
                PageRequest pageable = PageRequest.of(1, 5, Sort.by(
                                Sort.Order.desc("completedAt"),
                                Sort.Order.desc("id")
                ));
                when(contentRepository.searchCompletedByOwner(ownerId, ContentStatus.COMPLETED, "Example", pageable))
                                .thenReturn(new PageImpl<>(List.of(item), pageable, 6));

                HistoryPageResponse response = contentService.getHistory(" Example ", 1, 5);

                assertEquals(1, response.items().size());
                assertEquals(ContentStatus.COMPLETED, response.items().get(0).status());
                assertEquals(completedAt, response.items().get(0).completedAt());
                assertEquals(1, response.page());
                assertEquals(6, response.totalItems());
                assertEquals(2, response.totalPages());
                verify(contentRepository).searchCompletedByOwner(ownerId, ContentStatus.COMPLETED, "Example", pageable);
        }

        @Test
        void normalizesBlankHistorySearchAndRejectsInvalidPagination() {
                when(contentRepository.searchCompletedByOwner(any(), eq(ContentStatus.COMPLETED), eq(""), any()))
                                .thenReturn(new PageImpl<>(List.of()));

                contentService.getHistory("  ", 0, 20);

                verify(contentRepository).searchCompletedByOwner(any(), eq(ContentStatus.COMPLETED), eq(""), any());
                assertThrows(ResponseStatusException.class, () -> contentService.getHistory(null, -1, 20));
                assertThrows(ResponseStatusException.class, () -> contentService.getHistory(null, 0, 101));
        }

        @Test
        void updatesTitleForPendingContent() {
                UUID id = UUID.randomUUID();
                ContentItem item = new ContentItem(
                                UUID.randomUUID(), "https://example.org/lesson", "https://example.org/lesson",
                                "Old title", ContentType.ARTICLE, "example.org"
                );
                when(contentRepository.findByIdAndOwnerId(any(), any())).thenReturn(Optional.of(item));

                ContentResponse response = contentService.updateContent(id, new UpdateContentRequest("  New title  "));

                assertEquals("New title", response.title());
        }

        @Test
        void updatesEstimatedDurationWithoutChangingTheTitle() {
                UUID id = UUID.randomUUID();
                ContentItem item = new ContentItem(
                                UUID.randomUUID(), "https://example.org/lesson", "https://example.org/lesson",
                                "Existing title", ContentType.ARTICLE, "example.org"
                );
                UpdateContentRequest request = new UpdateContentRequest();
                request.setEstimatedDurationMinutes(18);
                when(contentRepository.findByIdAndOwnerId(any(), any())).thenReturn(Optional.of(item));

                ContentResponse response = contentService.updateContent(id, request);

                assertEquals("Existing title", response.title());
                assertEquals(18, response.estimatedDurationMinutes());
        }

        @Test
        void clearsEstimatedDurationWhenExplicitlySetToNull() {
                UUID id = UUID.randomUUID();
                ContentItem item = new ContentItem(
                                UUID.randomUUID(), "https://example.org/lesson", "https://example.org/lesson",
                                "Existing title", ContentType.ARTICLE, "example.org"
                );
                item.updateEstimatedDurationMinutes(18);
                UpdateContentRequest request = new UpdateContentRequest();
                request.setEstimatedDurationMinutes(null);
                when(contentRepository.findByIdAndOwnerId(any(), any())).thenReturn(Optional.of(item));

                ContentResponse response = contentService.updateContent(id, request);

                assertEquals(null, response.estimatedDurationMinutes());
        }

        @Test
        void rejectsInvalidOrEmptyContentUpdates() {
                for (Integer duration : List.of(0, -1)) {
                        UpdateContentRequest request = new UpdateContentRequest();
                        request.setEstimatedDurationMinutes(duration);
                        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                        () -> contentService.updateContent(UUID.randomUUID(), request));
                        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                }

                ResponseStatusException emptyException = assertThrows(ResponseStatusException.class,
                                () -> contentService.updateContent(UUID.randomUUID(), new UpdateContentRequest()));
                assertEquals(HttpStatus.BAD_REQUEST, emptyException.getStatusCode());
        }

        @Test
        void rejectsBlankTitleUpdate() {
                assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                                contentService.updateContent(UUID.randomUUID(), new UpdateContentRequest("  ")));
        }

        @Test
        void completesPendingContentAndKeepsItsFirstCompletionTimestamp() {
                UUID id = UUID.randomUUID();
                ContentItem item = new ContentItem(
                                UUID.randomUUID(), "https://example.org/lesson", "https://example.org/lesson",
                                "Lesson", ContentType.ARTICLE, "example.org"
                );
                when(contentRepository.findByIdAndOwnerIdForUpdate(any(), any())).thenReturn(Optional.of(item));
                when(contentRepository.saveAndFlush(item)).thenReturn(item);

                ContentResponse firstResponse = contentService.completeContent(id);
                Instant firstCompletion = firstResponse.completedAt();
                ContentResponse repeatedResponse = contentService.completeContent(id);

                assertEquals(ContentStatus.COMPLETED, firstResponse.status());
                assertEquals(firstCompletion, repeatedResponse.completedAt());
                assertEquals(ContentStatus.COMPLETED, repeatedResponse.status());
        }

        @Test
        void rejectsDirectCompletionOfContentAssignedToAnActiveSessionWithoutMutation() {
                UUID id = UUID.randomUUID();
                UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
                ContentItem item = new ContentItem(
                                ownerId, "https://example.org/lesson", "https://example.org/lesson",
                                "Lesson", ContentType.ARTICLE, "example.org"
                );
                when(contentRepository.findByIdAndOwnerIdForUpdate(id, ownerId)).thenReturn(Optional.of(item));
                when(sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(any(), eq(id), any())).thenReturn(true);

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                () -> contentService.completeContent(id));

                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                assertEquals(ContentStatus.PENDING, item.getStatus());
                assertEquals(null, item.getCompletedAt());
                verify(contentRepository, never()).saveAndFlush(any(ContentItem.class));
        }

    @Test
    void getsContentOnlyWithinTheLocalOwnerContext() {
        UUID id = UUID.randomUUID();
        ContentItem item = new ContentItem(
                UUID.randomUUID(), "https://example.org/lesson", "https://example.org/lesson",
                "Lesson", ContentType.ARTICLE, "example.org"
        );
        when(contentRepository.findByIdAndOwnerId(id, UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .thenReturn(Optional.of(item));

        ContentResponse response = contentService.getContent(id);

        assertEquals("Lesson", response.title());
        verify(contentRepository).findByIdAndOwnerId(id, UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

        @Test
        void passesAnEmptySearchTermWhenSearchIsBlank() {
                when(contentRepository.searchByOwner(any(), any(), any(), any(), any()))
                                .thenReturn(new PageImpl<>(List.of()));

                contentService.getContent(ContentStatus.PENDING, null, "  ", 0, 20);

                verify(contentRepository).searchByOwner(any(), any(), any(), eq(""), any());
        }

        @Test
        void returnsNotFoundWhenContentIsOutsideTheLocalOwnerContext() {
                when(contentRepository.findByIdAndOwnerId(any(), any())).thenReturn(Optional.empty());

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                () -> contentService.getContent(UUID.randomUUID()));

                assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        }

    @Test
    void deletesPendingContent() {
        UUID id = UUID.randomUUID();
        ContentItem item = new ContentItem(
                UUID.randomUUID(), "https://example.org/lesson", "https://example.org/lesson",
                "Lesson", ContentType.ARTICLE, "example.org"
        );
        when(contentRepository.findByIdAndOwnerId(any(), any())).thenReturn(Optional.of(item));

        contentService.deleteContent(id);

        verify(contentRepository).delete(item);
    }

    @Test
    void rejectsDeletingCompletedContent() {
        ContentItem item = new ContentItem(
                UUID.randomUUID(), "https://example.org/lesson", "https://example.org/lesson",
                "Lesson", ContentType.ARTICLE, "example.org"
        );
        item.markCompleted(Instant.parse("2026-10-23T11:00:00Z"));
        when(contentRepository.findByIdAndOwnerId(any(), any())).thenReturn(Optional.of(item));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> contentService.deleteContent(UUID.randomUUID()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

        @Test
        void rejectsDeletingContentAssignedToAnActiveSession() {
                UUID id = UUID.randomUUID();
                ContentItem item = new ContentItem(
                                UUID.randomUUID(), "https://example.org/lesson", "https://example.org/lesson",
                                "Lesson", ContentType.ARTICLE, "example.org"
                );
                when(contentRepository.findByIdAndOwnerId(any(), any())).thenReturn(Optional.of(item));
                when(sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(any(), any(), any())).thenReturn(true);

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                () -> contentService.deleteContent(id));

                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                verify(contentRepository, never()).delete(item);
        }
}