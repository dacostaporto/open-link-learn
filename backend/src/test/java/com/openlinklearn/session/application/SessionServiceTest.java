package com.openlinklearn.session.application;

import com.openlinklearn.content.domain.ContentItem;
import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.profile.application.SettingsService;
import com.openlinklearn.profile.application.dto.SettingsResponse;
import com.openlinklearn.session.application.cutoff.AssignmentCutoffJobService;
import com.openlinklearn.session.application.dto.CreateSessionRequest;
import com.openlinklearn.session.application.dto.SessionListResponse;
import com.openlinklearn.session.application.dto.SessionResponse;
import com.openlinklearn.session.application.dto.UpdateSessionRequest;
import com.openlinklearn.session.application.reminder.ReminderJobService;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ContentRepository contentRepository = mock(ContentRepository.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final AssignmentCutoffJobService assignmentCutoffJobService = mock(AssignmentCutoffJobService.class);
    private final ReminderJobService reminderJobService = mock(ReminderJobService.class);
    private final SessionService sessionService = new SessionService(
            sessionRepository, contentRepository, settingsService, assignmentCutoffJobService, reminderJobService);

    @Test
    void createsSessionUsingConfiguredDefaultsAndCalculatesEndTime() {
        when(settingsService.getSettings()).thenReturn(new SettingsResponse("Europe/Madrid", 10, 30, 90));
        when(sessionRepository.saveAndFlush(any(LearningSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionResponse response = sessionService.createSession(new CreateSessionRequest(
                Instant.parse("2026-10-26T06:30:00Z"), 30, null, null, null, null
        ));

        assertEquals(Instant.parse("2026-10-26T07:00:00Z"), response.endsAt());
        assertEquals("Europe/Madrid", response.timezone());
        assertEquals(10, response.reminderMinutesBefore());
        assertEquals(30, response.assignmentCutoffMinutesBefore());
        assertEquals(SessionStatus.PLANNED, response.status());
        assertNull(response.content());
        verify(assignmentCutoffJobService).scheduleForCreatedSession(any(UUID.class), any(Instant.class));
        verify(reminderJobService).scheduleForCreatedSession(any(UUID.class));
    }

    @Test
    void createsSessionWithAnEligiblePendingContentItem() {
        UUID contentId = UUID.randomUUID();
        ContentItem content = new ContentItem(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "https://example.org/lesson", "https://example.org/lesson", "Lesson", ContentType.ARTICLE, "example.org"
        );
        when(settingsService.getSettings()).thenReturn(new SettingsResponse("Etc/UTC", 10, 30, 90));
        when(contentRepository.findByIdAndOwnerIdForUpdate(any(), any())).thenReturn(Optional.of(content));
        when(sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(sessionRepository.saveAndFlush(any(LearningSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionResponse response = sessionService.createSession(new CreateSessionRequest(
                Instant.parse("2026-10-26T06:30:00Z"), 30, "Etc/UTC", contentId, 5, 15
        ));

        assertEquals("Lesson", response.content().title());
        assertEquals(5, response.reminderMinutesBefore());
        assertEquals(15, response.assignmentCutoffMinutesBefore());
    }

    @Test
    void rejectsInvalidDurationAndTimezone() {
        when(settingsService.getSettings()).thenReturn(new SettingsResponse("Etc/UTC", 10, 30, 90));

        ResponseStatusException durationError = assertThrows(ResponseStatusException.class, () ->
                sessionService.createSession(new CreateSessionRequest(
                        Instant.parse("2026-10-26T06:30:00Z"), 0, null, null, null, null
                )));
        assertEquals(HttpStatus.BAD_REQUEST, durationError.getStatusCode());

        ResponseStatusException timezoneError = assertThrows(ResponseStatusException.class, () ->
                sessionService.createSession(new CreateSessionRequest(
                        Instant.parse("2026-10-26T06:30:00Z"), 30, "Not/AZone", null, null, null
                )));
        assertEquals(HttpStatus.BAD_REQUEST, timezoneError.getStatusCode());
    }

    @Test
    void listsSessionsOnlyWithinRequestedOwnerAndRange() {
        Instant from = Instant.parse("2026-10-26T00:00:00Z");
        Instant to = Instant.parse("2026-10-27T00:00:00Z");
        when(sessionRepository.findOverlappingByOwner(
                any(), any(), any()
        )).thenReturn(List.of());

        assertEquals(List.of(), sessionService.getSessions(from, to).items());
        verify(sessionRepository).findOverlappingByOwner(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), from, to
        );
    }

        @Test
        void includesSeriesIdWhenListingRecurringOccurrences() {
                Instant from = Instant.parse("2026-10-26T00:00:00Z");
                Instant to = Instant.parse("2026-10-27T00:00:00Z");
                UUID seriesId = UUID.randomUUID();
                LearningSession occurrence = plannedSession(null);
                ReflectionTestUtils.setField(occurrence, "seriesId", seriesId);
                when(sessionRepository.findOverlappingByOwner(any(), any(), any())).thenReturn(List.of(occurrence));

                SessionListResponse response = sessionService.getSessions(from, to);

                assertEquals(seriesId, response.items().get(0).seriesId());
        }

    @Test
    void rejectsContentAlreadyReservedByAnotherActiveSession() {
        UUID contentId = UUID.randomUUID();
        ContentItem content = new ContentItem(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "https://example.org/lesson", "https://example.org/lesson", "Lesson", ContentType.ARTICLE, "example.org"
        );
        when(settingsService.getSettings()).thenReturn(new SettingsResponse("Etc/UTC", 10, 30, 90));
        when(contentRepository.findByIdAndOwnerIdForUpdate(any(), any())).thenReturn(Optional.of(content));
        when(sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(any(), any(), any())).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                sessionService.createSession(new CreateSessionRequest(
                        Instant.parse("2026-10-26T06:30:00Z"), 30, null, contentId, null, null
                )));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void cancelsPendingCutoffJobsWhenSessionsCompleteSkipOrCancel() {
        UUID completedId = UUID.randomUUID();
        UUID skippedId = UUID.randomUUID();
        UUID cancelledId = UUID.randomUUID();
        when(sessionRepository.findByIdAndOwnerIdForUpdate(completedId, ownerId()))
                .thenReturn(Optional.of(plannedSession(null)));
        when(sessionRepository.findByIdAndOwnerIdForUpdate(skippedId, ownerId()))
                .thenReturn(Optional.of(plannedSession(null)));
        when(sessionRepository.findByIdAndOwnerIdForUpdate(cancelledId, ownerId()))
                .thenReturn(Optional.of(plannedSession(null)));
        when(sessionRepository.saveAndFlush(any(LearningSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.completeSession(completedId);
        sessionService.skipSession(skippedId);
        sessionService.cancelSession(cancelledId);

        verify(assignmentCutoffJobService).cancelPendingForSession(completedId);
        verify(assignmentCutoffJobService).cancelPendingForSession(skippedId);
        verify(assignmentCutoffJobService).cancelPendingForSession(cancelledId);
        verify(assignmentCutoffJobService, times(3)).cancelPendingForSession(any(UUID.class));
        verify(reminderJobService).cancelPendingForSession(completedId);
        verify(reminderJobService).cancelPendingForSession(skippedId);
        verify(reminderJobService).cancelPendingForSession(cancelledId);
    }

        @Test
        void updatesSessionTimeDurationContentAndOverrides() {
                UUID sessionId = UUID.randomUUID();
                UUID contentId = UUID.randomUUID();
                LearningSession session = plannedSession(null);
                ContentItem content = content(contentId);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));
                when(contentRepository.findByIdAndOwnerIdForUpdate(contentId, ownerId())).thenReturn(Optional.of(content));
                when(sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(eq(ownerId()), eq(contentId), any()))
                                .thenReturn(false);
                when(sessionRepository.saveAndFlush(any(LearningSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

                UpdateSessionRequest request = new UpdateSessionRequest();
                request.setStartsAt(Instant.parse("2026-10-26T07:00:00Z"));
                request.setDurationMinutes(25);
                request.setContentItemId(contentId);
                request.setReminderMinutesBefore(10);
                request.setAssignmentCutoffMinutesBefore(45);

                SessionResponse response = sessionService.updateSession(sessionId, request);

                assertEquals(Instant.parse("2026-10-26T07:00:00Z"), response.startsAt());
                assertEquals(Instant.parse("2026-10-26T07:25:00Z"), response.endsAt());
                assertEquals("Lesson", response.content().title());
                assertEquals(10, response.reminderMinutesBefore());
                assertEquals(45, response.assignmentCutoffMinutesBefore());
                assertEquals(SessionStatus.PLANNED, response.status());
                assertNull(response.completedAt());
                verify(sessionRepository).findByIdAndOwnerIdForUpdate(sessionId, ownerId());
                verify(assignmentCutoffJobService).refreshForSession(sessionId);
                verify(reminderJobService).refreshForSession(sessionId);
        }

        @Test
        void preservesDurationWhenOnlyStartChangesAndAllowsExplicitContentRemoval() {
                UUID sessionId = UUID.randomUUID();
                ContentItem existingContent = content(UUID.randomUUID());
                LearningSession session = plannedSession(existingContent);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));
                when(sessionRepository.saveAndFlush(any(LearningSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

                UpdateSessionRequest request = new UpdateSessionRequest();
                request.setStartsAt(Instant.parse("2026-10-27T08:00:00Z"));
                request.setContentItemId(null);

                SessionResponse response = sessionService.updateSession(sessionId, request);

                assertEquals(Instant.parse("2026-10-27T08:30:00Z"), response.endsAt());
                assertNull(response.content());
        }

        @Test
        void rejectsInvalidUpdateRanges() {
                UUID sessionId = UUID.randomUUID();
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(plannedSession(null)));

                UpdateSessionRequest invalidDuration = new UpdateSessionRequest();
                invalidDuration.setDurationMinutes(721);
                ResponseStatusException durationError = assertThrows(ResponseStatusException.class,
                                () -> sessionService.updateSession(sessionId, invalidDuration));
                assertEquals(HttpStatus.BAD_REQUEST, durationError.getStatusCode());

                UpdateSessionRequest invalidReminder = new UpdateSessionRequest();
                invalidReminder.setReminderMinutesBefore(-1);
                ResponseStatusException reminderError = assertThrows(ResponseStatusException.class,
                                () -> sessionService.updateSession(sessionId, invalidReminder));
                assertEquals(HttpStatus.BAD_REQUEST, reminderError.getStatusCode());

                UpdateSessionRequest invalidCutoff = new UpdateSessionRequest();
                invalidCutoff.setAssignmentCutoffMinutesBefore(10081);
                ResponseStatusException cutoffError = assertThrows(ResponseStatusException.class,
                                () -> sessionService.updateSession(sessionId, invalidCutoff));
                assertEquals(HttpStatus.BAD_REQUEST, cutoffError.getStatusCode());

                UpdateSessionRequest nullStart = new UpdateSessionRequest();
                nullStart.setStartsAt(null);
                assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                                () -> sessionService.updateSession(sessionId, nullStart)).getStatusCode());
        }

        @Test
        void rejectsMissingSessionAndIneligibleContent() {
                UUID sessionId = UUID.randomUUID();
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.empty());
                UpdateSessionRequest request = new UpdateSessionRequest();
                request.setDurationMinutes(25);
                assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                                () -> sessionService.updateSession(sessionId, request)).getStatusCode());

                UUID contentId = UUID.randomUUID();
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(plannedSession(null)));
                when(contentRepository.findByIdAndOwnerIdForUpdate(contentId, ownerId())).thenReturn(Optional.of(content(contentId)));
                when(sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(eq(ownerId()), eq(contentId), any()))
                                .thenReturn(true);
                UpdateSessionRequest assignedContent = new UpdateSessionRequest();
                assignedContent.setContentItemId(contentId);
                assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                                () -> sessionService.updateSession(sessionId, assignedContent)).getStatusCode());
        }

        @Test
        void translatesAssignmentConstraintRaceToConflict() {
                UUID sessionId = UUID.randomUUID();
                LearningSession session = plannedSession(null);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));
                when(sessionRepository.saveAndFlush(session)).thenThrow(new DataIntegrityViolationException("duplicate"));
                UpdateSessionRequest request = new UpdateSessionRequest();
                request.setDurationMinutes(25);

                assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                                () -> sessionService.updateSession(sessionId, request)).getStatusCode());
        }

        @Test
        void completesSessionWithoutContentAndIsIdempotent() {
                UUID sessionId = UUID.randomUUID();
                LearningSession session = plannedSession(null);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));
                when(sessionRepository.saveAndFlush(session)).thenReturn(session);

                SessionResponse first = sessionService.completeSession(sessionId);
                SessionResponse repeated = sessionService.completeSession(sessionId);

                assertEquals(SessionStatus.COMPLETED, first.status());
                assertEquals(first.completedAt(), repeated.completedAt());
                assertEquals(first.completedAt(), session.getCompletedAt());
                assertNull(first.content());
                verify(contentRepository, never()).saveAndFlush(any(ContentItem.class));
                verify(sessionRepository).saveAndFlush(session);
        }

        @Test
        void completesAssignedContentInTheSameLifecycleTransition() {
                UUID sessionId = UUID.randomUUID();
                ContentItem content = content(UUID.randomUUID());
                LearningSession session = plannedSession(content);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));
                when(contentRepository.findByIdAndOwnerIdForUpdate(content.getId(), ownerId())).thenReturn(Optional.of(content));
                when(contentRepository.saveAndFlush(content)).thenReturn(content);
                when(sessionRepository.saveAndFlush(session)).thenReturn(session);

                SessionResponse response = sessionService.completeSession(sessionId);

                assertEquals(SessionStatus.COMPLETED, response.status());
                assertEquals(ContentStatus.COMPLETED, content.getStatus());
                assertEquals(response.completedAt(), content.getCompletedAt());
                assertEquals(response.completedAt(), session.getCompletedAt());
                verify(contentRepository).saveAndFlush(content);
                verify(sessionRepository).saveAndFlush(session);
        }

        @Test
        void returnsAlreadyCompletedSessionWithoutChangingCompletionHistory() {
                UUID sessionId = UUID.randomUUID();
                LearningSession session = plannedSession(null);
                Instant originalCompletedAt = Instant.parse("2026-10-20T12:00:00Z");
                session.markCompleted(originalCompletedAt);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));

                SessionResponse response = sessionService.completeSession(sessionId);

                assertEquals(SessionStatus.COMPLETED, response.status());
                assertEquals(originalCompletedAt, response.completedAt());
                verify(sessionRepository, never()).saveAndFlush(any(LearningSession.class));
        }

        @Test
        void rejectsInvalidSessionTransitionWithoutMutatingAssignedContent() {
                UUID sessionId = UUID.randomUUID();
                ContentItem content = content(UUID.randomUUID());
                LearningSession session = plannedSession(content);
                ReflectionTestUtils.setField(session, "status", SessionStatus.CANCELLED);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                () -> sessionService.completeSession(sessionId));

                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                assertEquals(SessionStatus.CANCELLED, session.getStatus());
                assertNull(session.getCompletedAt());
                assertEquals(ContentStatus.PENDING, content.getStatus());
                assertNull(content.getCompletedAt());
                verify(contentRepository, never()).saveAndFlush(any(ContentItem.class));
                verify(sessionRepository, never()).saveAndFlush(any(LearningSession.class));
        }

        @Test
        void returnsNotFoundWhenCompletingUnknownSession() {
                UUID sessionId = UUID.randomUUID();
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.empty());

                assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                                () -> sessionService.completeSession(sessionId)).getStatusCode());
        }

            @Test
            void skipsSessionAndReleasesPendingContentIdempotently() {
                UUID sessionId = UUID.randomUUID();
                ContentItem content = content(UUID.randomUUID());
                LearningSession session = plannedSession(content);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));
                when(sessionRepository.saveAndFlush(session)).thenReturn(session);

                SessionResponse first = sessionService.skipSession(sessionId);
                SessionResponse repeated = sessionService.skipSession(sessionId);

                assertEquals(SessionStatus.SKIPPED, first.status());
                assertNull(first.content());
                assertEquals(first, repeated);
                assertEquals(ContentStatus.PENDING, content.getStatus());
                assertNull(session.getContentItem());
                assertNull(session.getCompletedAt());
                verify(sessionRepository).saveAndFlush(session);
            }

            @Test
            void cancellationReleasesContentWithoutCompletingIt() {
                UUID sessionId = UUID.randomUUID();
                ContentItem content = content(UUID.randomUUID());
                LearningSession session = plannedSession(content);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));
                when(sessionRepository.saveAndFlush(session)).thenReturn(session);

                SessionResponse response = sessionService.cancelSession(sessionId);

                assertEquals(SessionStatus.CANCELLED, response.status());
                assertNull(response.content());
                assertEquals(ContentStatus.PENDING, content.getStatus());
                assertNull(session.getContentItem());
                assertNull(session.getCompletedAt());
            }

            @Test
            void releasedContentCanBeAssignedToAnotherSession() {
                UUID sessionId = UUID.randomUUID();
                ContentItem content = content(UUID.randomUUID());
                LearningSession original = plannedSession(content);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(original));
                when(sessionRepository.saveAndFlush(any(LearningSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(settingsService.getSettings()).thenReturn(new SettingsResponse("Etc/UTC", 10, 30, 90));
                when(contentRepository.findByIdAndOwnerIdForUpdate(content.getId(), ownerId())).thenReturn(Optional.of(content));
                when(sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(eq(ownerId()), eq(content.getId()), any()))
                        .thenReturn(false);

                SessionResponse skipped = sessionService.skipSession(sessionId);
                SessionResponse reassigned = sessionService.createSession(new CreateSessionRequest(
                        Instant.parse("2026-10-27T06:30:00Z"), 30, "Etc/UTC", content.getId(), null, null
                ));

                assertEquals(SessionStatus.SKIPPED, skipped.status());
                assertEquals("Lesson", reassigned.content().title());
                assertEquals(ContentStatus.PENDING, content.getStatus());
                verify(sessionRepository).existsByOwnerIdAndContentItem_IdAndStatusIn(eq(ownerId()), eq(content.getId()), any());
            }

            @Test
            void rejectsCrossTerminalAndCompletedSessionTransitionsWithoutChangingHistory() {
                UUID sessionId = UUID.randomUUID();
                LearningSession skipped = plannedSession(null);
                skipped.skipAndReleaseContent();
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(skipped));

                ResponseStatusException crossTerminal = assertThrows(ResponseStatusException.class,
                        () -> sessionService.cancelSession(sessionId));
                assertEquals(HttpStatus.CONFLICT, crossTerminal.getStatusCode());
                verify(sessionRepository, never()).saveAndFlush(any(LearningSession.class));

                LearningSession completed = plannedSession(null);
                Instant completedAt = Instant.parse("2026-10-20T12:00:00Z");
                completed.markCompleted(completedAt);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(completed));
                ResponseStatusException completedError = assertThrows(ResponseStatusException.class,
                        () -> sessionService.skipSession(sessionId));

                assertEquals(HttpStatus.CONFLICT, completedError.getStatusCode());
                assertEquals(SessionStatus.COMPLETED, completed.getStatus());
                assertEquals(completedAt, completed.getCompletedAt());
            }

        @Test
        void rejectsCancellingAnInProgressSession() {
                UUID sessionId = UUID.randomUUID();
                LearningSession session = plannedSession(null);
                ReflectionTestUtils.setField(session, "status", SessionStatus.IN_PROGRESS);
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.of(session));

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                () -> sessionService.cancelSession(sessionId));

                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                assertEquals(SessionStatus.IN_PROGRESS, session.getStatus());
                verify(sessionRepository, never()).saveAndFlush(any(LearningSession.class));
        }

            @Test
            void returnsNotFoundForSkipAndCancelOutsideOwnerContext() {
                UUID sessionId = UUID.randomUUID();
                when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, ownerId())).thenReturn(Optional.empty());

                assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                        () -> sessionService.skipSession(sessionId)).getStatusCode());
                assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                        () -> sessionService.cancelSession(sessionId)).getStatusCode());
            }

        private LearningSession plannedSession(ContentItem content) {
                return new LearningSession(ownerId(), Instant.parse("2026-10-26T06:30:00Z"),
                                Instant.parse("2026-10-26T07:00:00Z"), "Etc/UTC", content, 10, 30);
        }

        private ContentItem content(UUID id) {
                return new ContentItem(ownerId(), "https://example.org/lesson", "https://example.org/lesson",
                                "Lesson", ContentType.ARTICLE, "example.org") {
                        @Override
                        public UUID getId() {
                                return id;
                        }
                };
        }

        private UUID ownerId() {
                return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
}