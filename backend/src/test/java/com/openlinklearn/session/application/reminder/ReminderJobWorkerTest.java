package com.openlinklearn.session.application.reminder;

import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.ReminderDeliveryException;
import com.openlinklearn.session.domain.ReminderJob;
import com.openlinklearn.session.domain.ReminderJobRepository;
import com.openlinklearn.session.domain.ReminderSender;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.infrastructure.ScheduledReminderWorker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderJobWorkerTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00Z");

    @Test
    void processorSendsOutsideStateTransactionsAndMarksTheClaimSent() {
        ReminderJobDeliveryStateService stateService = mock(ReminderJobDeliveryStateService.class);
        TestReminderSender sender = new TestReminderSender();
        ReminderJobProcessor processor = new ReminderJobProcessor(stateService, sender);
        ReminderJobClaimService.Claim claim = claim();
        ReminderSender.ReminderDelivery delivery = delivery(claim);
        when(stateService.getDelivery(claim)).thenReturn(Optional.of(delivery));
        when(stateService.markSent(claim, NOW)).thenReturn(true);

        assertTrue(processor.process(claim, NOW));

        assertEquals(List.of(delivery), sender.deliveries);
        inOrder(stateService).verify(stateService).getDelivery(claim);
        inOrder(stateService).verify(stateService).markSent(claim, NOW);
    }

    @Test
    void transientAndUnexpectedFailuresAreRetriedButPermanentFailuresAreNot() {
        ReminderJobDeliveryStateService stateService = mock(ReminderJobDeliveryStateService.class);
        TestReminderSender sender = new TestReminderSender();
        ReminderJobProcessor processor = new ReminderJobProcessor(stateService, sender);
        ReminderJobClaimService.Claim claim = claim();
        when(stateService.getDelivery(claim)).thenReturn(Optional.of(
            delivery(claim)));

        sender.failure = new ReminderDeliveryException(true, "temporary provider outage");
        assertFalse(processor.process(claim, NOW));
        verify(stateService).recordFailure(claim, NOW, "ReminderDeliveryException", true);

        sender.failure = new ReminderDeliveryException(false, "permanent provider rejection");
        assertFalse(processor.process(claim, NOW));
        verify(stateService).recordFailure(claim, NOW, "ReminderDeliveryException", false);

        sender.failure = new IllegalStateException("unexpected failure");
        assertFalse(processor.process(claim, NOW));
        verify(stateService).recordFailure(claim, NOW, "IllegalStateException", true);
    }

    @Test
    void processorDoesNotSendOrMutateWhenClaimTokenIsStale() {
        ReminderJobDeliveryStateService stateService = mock(ReminderJobDeliveryStateService.class);
        TestReminderSender sender = new TestReminderSender();
        ReminderJobProcessor processor = new ReminderJobProcessor(stateService, sender);
        ReminderJobClaimService.Claim claim = claim();
        when(stateService.getDelivery(claim)).thenReturn(Optional.empty());

        assertFalse(processor.process(claim, NOW));

        assertTrue(sender.deliveries.isEmpty());
        verify(stateService, never()).markSent(any(), any());
        verify(stateService, never()).recordFailure(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

        @Test
        void deliveryLoadsOwnerAndTimingFromPersistedSession() {
        ReminderJobRepository jobRepository = mock(ReminderJobRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        LearningSession session = new LearningSession(
            OWNER_ID, NOW.plusSeconds(600), NOW.plusSeconds(3600), "Etc/UTC", null, 10, 30);
        UUID sessionId = session.getId();
        UUID token = UUID.randomUUID();
        ReminderJob job = new ReminderJob(OWNER_ID, sessionId, ReminderJob.Kind.REMINDER, NOW, "delivery-state-test");
        job.claim(token, NOW);
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(sessionRepository.findByIdAndOwnerId(sessionId, OWNER_ID)).thenReturn(Optional.of(session));
        ReminderJobDeliveryStateService stateService = new ReminderJobDeliveryStateService(
            jobRepository, sessionRepository);

        Optional<ReminderSender.ReminderDelivery> delivery = stateService.getDelivery(
            new ReminderJobClaimService.Claim(job.getId(), sessionId, token));

        assertTrue(delivery.isPresent());
        assertEquals(OWNER_ID, delivery.get().ownerId());
        assertEquals(sessionId, delivery.get().sessionId());
        assertEquals(NOW.plusSeconds(600), delivery.get().startsAt());
        assertEquals(10, delivery.get().minutesBefore());
        }

    @Test
    void workerDoesNotProcessTheSameClaimOnRepeatedInvocation() {
        ReminderJobClaimService claimService = mock(ReminderJobClaimService.class);
        ReminderJobProcessor processor = mock(ReminderJobProcessor.class);
        ScheduledReminderWorker worker = new ScheduledReminderWorker(claimService, processor);
        ReminderJobClaimService.Claim claim = claim();
        when(claimService.recoverAndClaim(NOW, NOW.minusSeconds(300), 50))
                .thenReturn(List.of(claim), List.of());

        assertEquals(1, worker.runDueJobs(NOW));
        assertEquals(0, worker.runDueJobs(NOW));

        verify(processor, times(1)).process(claim, NOW);
    }

    @Test
    void retriesUseCappedExponentialBackoffAndBecomePermanentlyFailedAtTheLimit() {
        ReminderJob job = new ReminderJob(
                OWNER_ID, UUID.randomUUID(), ReminderJob.Kind.REMINDER, NOW, "retry-limit");
        Instant attemptAt = NOW;

        for (int attempt = 1; attempt <= 10; attempt++) {
            job.claim(UUID.randomUUID(), attemptAt);
            job.recordFailure(attemptAt, "safe transient error");
            assertEquals(attempt, job.getAttemptCount());
            if (attempt < 10) {
                assertEquals(ReminderJob.Status.PENDING, job.getStatus());
                long retrySeconds = Math.min(300, 5L << Math.min(attempt - 1, 6));
                assertEquals(attemptAt.plusSeconds(retrySeconds), job.getNextAttemptAt());
                attemptAt = job.getNextAttemptAt();
            } else {
                assertEquals(ReminderJob.Status.FAILED, job.getStatus());
                assertEquals(null, job.getNextAttemptAt());
            }
        }
    }

    @Test
    void staleProcessingClaimReturnsToBoundedRetryPath() {
        ReminderJob job = new ReminderJob(
                OWNER_ID, UUID.randomUUID(), ReminderJob.Kind.REMINDER, NOW, "stale-claim");
        job.claim(UUID.randomUUID(), NOW.minusSeconds(301));

        job.recoverStaleClaim(NOW);

        assertEquals(ReminderJob.Status.PENDING, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertEquals(NOW.plusSeconds(5), job.getNextAttemptAt());
        assertEquals("Processing lease expired.", job.getLastError());
        assertEquals(null, job.getClaimToken());
    }

    private ReminderJobClaimService.Claim claim() {
        return new ReminderJobClaimService.Claim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private ReminderSender.ReminderDelivery delivery(ReminderJobClaimService.Claim claim) {
        return new ReminderSender.ReminderDelivery(
                OWNER_ID, claim.sessionId(), NOW.plusSeconds(600), 10);
    }

    private static final class TestReminderSender implements ReminderSender {
        private final List<ReminderDelivery> deliveries = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void send(ReminderDelivery delivery) {
            if (failure != null) {
                throw failure;
            }
            deliveries.add(delivery);
        }
    }
}