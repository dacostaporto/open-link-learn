package com.openlinklearn.session.infrastructure;

import com.openlinklearn.session.application.reminder.ReminderJobClaimService;
import com.openlinklearn.session.application.reminder.ReminderJobProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "openlinklearn.push", name = "enabled", havingValue = "true")
public class ScheduledReminderWorker {

    private static final int BATCH_SIZE = 50;
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);

    private final ReminderJobClaimService claimService;
    private final ReminderJobProcessor processor;

    public ScheduledReminderWorker(ReminderJobClaimService claimService, ReminderJobProcessor processor) {
        this.claimService = claimService;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${openlinklearn.reminder-worker.fixed-delay-ms:30000}")
    public void poll() {
        runDueJobs(Instant.now());
    }

    public int runDueJobs(Instant now) {
        List<ReminderJobClaimService.Claim> claims = claimService.recoverAndClaim(
                now, now.minus(CLAIM_LEASE), BATCH_SIZE);
        for (ReminderJobClaimService.Claim claim : claims) {
            processor.process(claim, now);
        }
        return claims.size();
    }
}