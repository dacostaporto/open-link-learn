package com.openlinklearn.session.application;

import com.openlinklearn.content.domain.ContentItem;
import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomaticContentAssignmentServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ContentRepository contentRepository = mock(ContentRepository.class);
    private final AutomaticContentAssignmentService service =
            new AutomaticContentAssignmentService(sessionRepository, contentRepository);

    @Test
    void leavesSessionUnassignedWhenNoEligiblePendingContentExists() {
        UUID sessionId = UUID.randomUUID();
        LearningSession session = plannedSession(null);
        when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, OWNER_ID)).thenReturn(Optional.of(session));
        when(contentRepository.findRandomPendingUnassignedForUpdate(OWNER_ID)).thenReturn(Optional.empty());

        assertTrue(service.assignPendingContent(sessionId).isEmpty());
        assertNull(session.getContentItem());
        verify(sessionRepository, never()).saveAndFlush(any(LearningSession.class));
    }

    @Test
    void preservesManualAssignmentWithoutSelectingOrSavingAnotherItem() {
        UUID sessionId = UUID.randomUUID();
        ContentItem manualContent = content();
        LearningSession session = plannedSession(manualContent);
        when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, OWNER_ID)).thenReturn(Optional.of(session));

        assertEquals(Optional.of(manualContent.getId()), service.assignPendingContent(sessionId));
        verify(contentRepository, never()).findRandomPendingUnassignedForUpdate(any());
        verify(sessionRepository, never()).saveAndFlush(any(LearningSession.class));
    }

    @Test
    void repeatedAssignmentReturnsTheExistingContentWithoutChangingIt() {
        UUID sessionId = UUID.randomUUID();
        ContentItem selectedContent = content();
        LearningSession session = plannedSession(null);
        when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, OWNER_ID)).thenReturn(Optional.of(session));
        when(contentRepository.findRandomPendingUnassignedForUpdate(OWNER_ID))
                .thenReturn(Optional.of(selectedContent));
        when(sessionRepository.saveAndFlush(session)).thenReturn(session);

        assertEquals(Optional.of(selectedContent.getId()), service.assignPendingContent(sessionId));
        assertEquals(Optional.of(selectedContent.getId()), service.assignPendingContent(sessionId));

        verify(contentRepository).findRandomPendingUnassignedForUpdate(OWNER_ID);
        verify(sessionRepository).saveAndFlush(session);
        assertEquals(selectedContent, session.getContentItem());
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"IN_PROGRESS", "COMPLETED", "SKIPPED", "CANCELLED"})
    void rejectsSessionsThatAreNotPlanned(SessionStatus status) {
        UUID sessionId = UUID.randomUUID();
        LearningSession session = plannedSession(null);
        ReflectionTestUtils.setField(session, "status", status);
        when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, OWNER_ID)).thenReturn(Optional.of(session));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.assignPendingContent(sessionId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(contentRepository, never()).findRandomPendingUnassignedForUpdate(any());
        verify(sessionRepository, never()).saveAndFlush(any(LearningSession.class));
    }

    private LearningSession plannedSession(ContentItem content) {
        return new LearningSession(OWNER_ID, Instant.parse("2026-10-26T06:30:00Z"),
                Instant.parse("2026-10-26T07:00:00Z"), "Etc/UTC", content, 10, 30);
    }

    private ContentItem content() {
        return new ContentItem(OWNER_ID, "https://example.org/lesson", "https://example.org/lesson",
                "Lesson", ContentType.ARTICLE, "example.org");
    }
}