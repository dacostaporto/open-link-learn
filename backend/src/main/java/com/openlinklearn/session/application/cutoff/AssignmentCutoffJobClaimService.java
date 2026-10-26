package com.openlinklearn.session.application.cutoff;

import com.openlinklearn.session.domain.ReminderJob;
import com.openlinklearn.session.domain.ReminderJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AssignmentCutoffJobClaimService {

    private final ReminderJobRepository reminderJobRepository;

    public AssignmentCutoffJobClaimService(ReminderJobRepository reminderJobRepository) {
        this.reminderJobRepository = reminderJobRepository;
    }

    @Transactional
    public List<Claim> recoverAndClaim(Instant now, Instant staleBefore, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be positive.");
        }

        List<ReminderJob> staleJobs = reminderJobRepository.lockStaleAssignmentCutoffJobs(staleBefore, batchSize);
        staleJobs.forEach(job -> job.recoverStaleClaim(now));
        if (!staleJobs.isEmpty()) {
            reminderJobRepository.saveAllAndFlush(staleJobs);
        }

        List<ReminderJob> dueJobs = reminderJobRepository.lockDueAssignmentCutoffJobs(now, batchSize);
        for (ReminderJob job : dueJobs) {
            job.claim(UUID.randomUUID(), now);
        }
        reminderJobRepository.saveAllAndFlush(dueJobs);
        return dueJobs.stream()
                .map(job -> new Claim(job.getId(), job.getSessionId(), job.getClaimToken()))
                .toList();
    }

    public record Claim(UUID jobId, UUID sessionId, UUID token) {
    }
}