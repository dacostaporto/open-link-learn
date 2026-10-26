package com.openlinklearn.session.application.reminder;

import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.ReminderJob;
import com.openlinklearn.session.domain.ReminderJobRepository;
import com.openlinklearn.session.domain.SessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderJobServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ReminderJobRepository reminderJobRepository = mock(ReminderJobRepository.class);
    private final ReminderJobService service = new ReminderJobService(sessionRepository, reminderJobRepository);

    @Test
    void schedulesUsingPersistedLeadTimeAndIsIdempotent() {
        Instant startsAt = Instant.parse("2026-10-26T06:30:00Z");
        LearningSession session = session(startsAt, 17);
        when(sessionRepository.findByIdAndOwnerIdForUpdate(session.getId(), OWNER_ID))
                .thenReturn(Optional.of(session));
        when(reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                session.getId(), ReminderJob.Kind.REMINDER,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING)))
                .thenReturn(List.of(), List.of(new ReminderJob(
                        OWNER_ID, session.getId(), ReminderJob.Kind.REMINDER,
                        startsAt.minusSeconds(17 * 60L), "existing")));
        when(reminderJobRepository.saveAndFlush(any(ReminderJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.scheduleForCreatedSession(session.getId());
        service.scheduleForCreatedSession(session.getId());

        var jobCaptor = org.mockito.ArgumentCaptor.forClass(ReminderJob.class);
        verify(reminderJobRepository).saveAndFlush(jobCaptor.capture());
        assertEquals(ReminderJob.Kind.REMINDER, jobCaptor.getValue().getKind());
        assertEquals(Instant.parse("2026-10-26T06:13:00Z"), jobCaptor.getValue().getScheduledAt());
        assertEquals(ReminderJob.Status.PENDING, jobCaptor.getValue().getStatus());
    }

    @Test
    void rescheduleCancelsOnlyOpenOldReminderAndKeepsSentHistory() {
        LearningSession session = session(Instant.parse("2026-10-26T07:00:00Z"), 10);
        ReminderJob pending = job(session.getId(), Instant.parse("2026-10-26T06:20:00Z"), "old-pending");
        ReminderJob processing = job(session.getId(), Instant.parse("2026-10-26T06:25:00Z"), "old-processing");
        processing.claim(UUID.randomUUID(), Instant.parse("2026-10-26T06:00:00Z"));
        ReminderJob sent = job(session.getId(), Instant.parse("2026-10-26T06:30:00Z"), "sent-history");
        sent.markProcessed(ReminderJob.Outcome.ASSIGNED, Instant.parse("2026-10-26T06:30:00Z"));
        when(sessionRepository.findByIdAndOwnerIdForUpdate(session.getId(), OWNER_ID))
                .thenReturn(Optional.of(session));
        when(reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                session.getId(), ReminderJob.Kind.REMINDER,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING)))
                .thenReturn(List.of(pending, processing));
        when(reminderJobRepository.saveAndFlush(any(ReminderJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.refreshForSession(session.getId());

        assertEquals(ReminderJob.Status.CANCELLED, pending.getStatus());
        assertEquals(ReminderJob.Status.CANCELLED, processing.getStatus());
        assertEquals(ReminderJob.Status.SENT, sent.getStatus());
        var jobCaptor = org.mockito.ArgumentCaptor.forClass(ReminderJob.class);
        verify(reminderJobRepository, times(3)).saveAndFlush(jobCaptor.capture());
        ReminderJob replacement = jobCaptor.getAllValues().get(2);
        assertEquals(Instant.parse("2026-10-26T06:50:00Z"), replacement.getScheduledAt());
        assertNotEquals(pending.getIdempotencyKey(), replacement.getIdempotencyKey());
    }

    @Test
    void keepsAnUnchangedPendingReminderAndDoesNotResendSentJobs() {
        Instant startsAt = Instant.parse("2026-10-26T06:30:00Z");
        LearningSession session = session(startsAt, 10);
        ReminderJob matching = job(session.getId(), startsAt.minusSeconds(600), "pending");
        when(sessionRepository.findByIdAndOwnerIdForUpdate(session.getId(), OWNER_ID))
                .thenReturn(Optional.of(session));
        when(reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                session.getId(), ReminderJob.Kind.REMINDER,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING)))
                .thenReturn(List.of(matching));

        service.refreshForSession(session.getId());

        verify(reminderJobRepository, never()).saveAndFlush(any(ReminderJob.class));
    }

    @Test
    void cancellationCancelsOnlyPendingAndProcessingReminders() {
        UUID sessionId = UUID.randomUUID();
        ReminderJob pending = job(sessionId, Instant.parse("2026-10-26T06:20:00Z"), "pending");
        ReminderJob processing = job(sessionId, Instant.parse("2026-10-26T06:25:00Z"), "processing");
        processing.claim(UUID.randomUUID(), Instant.parse("2026-10-26T06:00:00Z"));
        when(reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                sessionId, ReminderJob.Kind.REMINDER,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING)))
                .thenReturn(List.of(pending, processing));

        service.cancelPendingForSession(sessionId);

        assertEquals(ReminderJob.Status.CANCELLED, pending.getStatus());
        assertEquals(ReminderJob.Status.CANCELLED, processing.getStatus());
        verify(reminderJobRepository, times(2)).saveAndFlush(any(ReminderJob.class));
    }

    private ReminderJob job(UUID sessionId, Instant scheduledAt, String key) {
        return new ReminderJob(OWNER_ID, sessionId, ReminderJob.Kind.REMINDER, scheduledAt, key);
    }

    private LearningSession session(Instant startsAt, int reminderMinutes) {
        return new LearningSession(OWNER_ID, startsAt, startsAt.plusSeconds(1800), "Etc/UTC", null,
                reminderMinutes, 30);
    }
}