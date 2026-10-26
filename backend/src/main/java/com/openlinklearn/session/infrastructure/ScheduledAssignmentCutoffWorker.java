package com.openlinklearn.session.infrastructure;

import com.openlinklearn.session.application.cutoff.AssignmentCutoffJobClaimService;
import com.openlinklearn.session.application.cutoff.AssignmentCutoffJobProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class ScheduledAssignmentCutoffWorker {

    private static final int BATCH_SIZE = 50;
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);

    private final AssignmentCutoffJobClaimService claimService;
    private final AssignmentCutoffJobProcessor processor;

    public ScheduledAssignmentCutoffWorker(
            AssignmentCutoffJobClaimService claimService,
            AssignmentCutoffJobProcessor processor
    ) {
        this.claimService = claimService;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${openlinklearn.assignment-cutoff-worker.fixed-delay-ms:30000}")
    public void poll() {
        runDueJobs(Instant.now());
    }

    public int runDueJobs(Instant now) {
        List<AssignmentCutoffJobClaimService.Claim> claims = claimService.recoverAndClaim(
                now, now.minus(CLAIM_LEASE), BATCH_SIZE);
        for (AssignmentCutoffJobClaimService.Claim claim : claims) {
            try {
                processor.process(claim, now);
            } catch (RuntimeException exception) {
                processor.recordFailure(claim, now, exception.getClass().getSimpleName());
            }
        }
        return claims.size();
    }
}