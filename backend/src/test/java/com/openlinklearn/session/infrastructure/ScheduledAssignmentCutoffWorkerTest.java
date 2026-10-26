package com.openlinklearn.session.infrastructure;

import com.openlinklearn.session.application.cutoff.AssignmentCutoffJobClaimService;
import com.openlinklearn.session.application.cutoff.AssignmentCutoffJobProcessor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledAssignmentCutoffWorkerTest {

    private final AssignmentCutoffJobClaimService claimService = mock(AssignmentCutoffJobClaimService.class);
    private final AssignmentCutoffJobProcessor processor = mock(AssignmentCutoffJobProcessor.class);
    private final ScheduledAssignmentCutoffWorker worker = new ScheduledAssignmentCutoffWorker(
            claimService, processor);

    @Test
    void emptyBacklogDoesNotInvokeProcessor() {
        Instant now = Instant.parse("2026-10-26T06:00:00Z");
        when(claimService.recoverAndClaim(now, now.minusSeconds(300), 50)).thenReturn(List.of());

        assertEquals(0, worker.runDueJobs(now));

        verify(processor, never()).process(
                org.mockito.ArgumentMatchers.any(AssignmentCutoffJobClaimService.Claim.class),
                org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    void repeatedInvocationProcessesAClaimOnlyOnce() {
        Instant now = Instant.parse("2026-10-26T06:00:00Z");
        AssignmentCutoffJobClaimService.Claim claim = new AssignmentCutoffJobClaimService.Claim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(claimService.recoverAndClaim(now, now.minusSeconds(300), 50))
                .thenReturn(List.of(claim), List.of());

        assertEquals(1, worker.runDueJobs(now));
        assertEquals(0, worker.runDueJobs(now));

        verify(processor).process(claim, now);
    }
}