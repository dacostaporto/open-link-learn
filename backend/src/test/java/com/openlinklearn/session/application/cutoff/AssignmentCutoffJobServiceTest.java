package com.openlinklearn.session.application.cutoff;

import com.openlinklearn.session.application.AutomaticContentAssignmentService;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.ReminderJob;
import com.openlinklearn.session.domain.ReminderJobRepository;
import com.openlinklearn.session.domain.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentCutoffJobServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ReminderJobRepository reminderJobRepository = mock(ReminderJobRepository.class);
    private final AutomaticContentAssignmentService assignmentService = mock(AutomaticContentAssignmentService.class);
    private final AssignmentCutoffJobService service = new AssignmentCutoffJobService(
            sessionRepository, reminderJobRepository, assignmentService);

    @Test
    void schedulesAtSessionStartMinusPersistedCutoff() {
        Instant startsAt = Instant.parse("2026-10-26T06:30:00Z");
        LearningSession session = session(startsAt, 30);
        when(sessionRepository.findByIdAndOwnerIdForUpdate(session.getId(), OWNER_ID)).thenReturn(Optional.of(session));
        when(reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                session.getId(), ReminderJob.Kind.ASSIGNMENT_CUTOFF,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING))).thenReturn(List.of());
        when(reminderJobRepository.saveAndFlush(any(ReminderJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.scheduleForCreatedSession(session.getId(), Instant.parse("2026-10-26T05:00:00Z"));

        var jobCaptor = org.mockito.ArgumentCaptor.forClass(ReminderJob.class);
        verify(reminderJobRepository).saveAndFlush(jobCaptor.capture());
        assertEquals(Instant.parse("2026-10-26T06:00:00Z"), jobCaptor.getValue().getScheduledAt());
        assertEquals(ReminderJob.Kind.ASSIGNMENT_CUTOFF, jobCaptor.getValue().getKind());
        assertEquals(ReminderJob.Status.PENDING, jobCaptor.getValue().getStatus());
    }

    @Test
    void replacesChangedPendingCutoffAndKeepsOldJobCancelled() {
        LearningSession session = session(Instant.parse("2026-10-26T06:30:00Z"), 45);
        ReminderJob oldJob = new ReminderJob(OWNER_ID, session.getId(), ReminderJob.Kind.ASSIGNMENT_CUTOFF,
                Instant.parse("2026-10-26T06:00:00Z"), "old-key");
        when(sessionRepository.findByIdAndOwnerIdForUpdate(session.getId(), OWNER_ID)).thenReturn(Optional.of(session));
        when(reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                session.getId(), ReminderJob.Kind.ASSIGNMENT_CUTOFF,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING))).thenReturn(List.of(oldJob));
        when(reminderJobRepository.saveAndFlush(any(ReminderJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.refreshForSession(session.getId());

        assertEquals(ReminderJob.Status.CANCELLED, oldJob.getStatus());
        var jobCaptor = org.mockito.ArgumentCaptor.forClass(ReminderJob.class);
        verify(reminderJobRepository, org.mockito.Mockito.times(2)).saveAndFlush(jobCaptor.capture());
        ReminderJob replacement = jobCaptor.getAllValues().get(1);
        assertEquals(Instant.parse("2026-10-26T05:45:00Z"), replacement.getScheduledAt());
        assertNotEquals(oldJob.getIdempotencyKey(), replacement.getIdempotencyKey());
        assertEquals(ReminderJob.Status.PENDING, replacement.getStatus());
    }

    @Test
    void repeatedSchedulingKeepsMatchingPendingJob() {
        LearningSession session = session(Instant.parse("2026-10-26T06:30:00Z"), 30);
        ReminderJob existing = new ReminderJob(OWNER_ID, session.getId(), ReminderJob.Kind.ASSIGNMENT_CUTOFF,
                Instant.parse("2026-10-26T06:00:00Z"), "stable-key");
        when(sessionRepository.findByIdAndOwnerIdForUpdate(session.getId(), OWNER_ID)).thenReturn(Optional.of(session));
        when(reminderJobRepository.existsBySessionIdAndKindAndScheduledAt(
                session.getId(), ReminderJob.Kind.ASSIGNMENT_CUTOFF, existing.getScheduledAt())).thenReturn(false);
        when(reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                session.getId(), ReminderJob.Kind.ASSIGNMENT_CUTOFF,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING))).thenReturn(List.of(existing));

        service.scheduleForCreatedSession(session.getId(), Instant.parse("2026-10-26T05:00:00Z"));
        service.scheduleForCreatedSession(session.getId(), Instant.parse("2026-10-26T05:00:00Z"));

        verify(reminderJobRepository, never()).saveAndFlush(any(ReminderJob.class));
    }

    @Test
    void attemptsAssignmentImmediatelyForUpcomingSessionCreatedAfterCutoff() {
        Instant now = Instant.parse("2026-10-26T06:00:00Z");
        LearningSession session = session(now.plusSeconds(20 * 60L), 30);
        when(sessionRepository.findByIdAndOwnerIdForUpdate(session.getId(), OWNER_ID)).thenReturn(Optional.of(session));
        when(reminderJobRepository.existsBySessionIdAndKindAndScheduledAt(
                session.getId(), ReminderJob.Kind.ASSIGNMENT_CUTOFF, now.minusSeconds(10 * 60L)))
                .thenReturn(false, true);
        when(reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                session.getId(), ReminderJob.Kind.ASSIGNMENT_CUTOFF,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING))).thenReturn(List.of());
        when(reminderJobRepository.saveAndFlush(any(ReminderJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.scheduleForCreatedSession(session.getId(), now);
        service.scheduleForCreatedSession(session.getId(), now);

        verify(assignmentService).assignPendingContent(session.getId());
        var jobCaptor = org.mockito.ArgumentCaptor.forClass(ReminderJob.class);
        verify(reminderJobRepository, org.mockito.Mockito.times(2)).saveAndFlush(jobCaptor.capture());
        assertEquals(ReminderJob.Status.SENT, jobCaptor.getAllValues().get(0).getStatus());
        assertEquals(ReminderJob.Status.SENT, jobCaptor.getAllValues().get(1).getStatus());
    }

    @Test
    void doesNotAttemptLateAssignmentForSessionThatAlreadyStarted() {
        Instant now = Instant.parse("2026-10-26T06:00:00Z");
        LearningSession session = session(now.minusSeconds(60), 30);
        when(sessionRepository.findByIdAndOwnerIdForUpdate(session.getId(), OWNER_ID)).thenReturn(Optional.of(session));
        when(reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                session.getId(), ReminderJob.Kind.ASSIGNMENT_CUTOFF,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING))).thenReturn(List.of());
        when(reminderJobRepository.saveAndFlush(any(ReminderJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.scheduleForCreatedSession(session.getId(), now);

        verify(assignmentService, never()).assignPendingContent(session.getId());
    }

    @Test
    void rejectsMissingSession() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, OWNER_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.scheduleForCreatedSession(sessionId, Instant.now()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private LearningSession session(Instant startsAt, int cutoffMinutes) {
        return new LearningSession(OWNER_ID, startsAt, startsAt.plusSeconds(1800), "Etc/UTC", null, 10,
                cutoffMinutes);
    }
}