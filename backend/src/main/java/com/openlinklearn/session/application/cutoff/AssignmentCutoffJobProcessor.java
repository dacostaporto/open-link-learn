package com.openlinklearn.session.application.cutoff;

import com.openlinklearn.session.application.AutomaticContentAssignmentService;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.ReminderJob;
import com.openlinklearn.session.domain.ReminderJobRepository;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AssignmentCutoffJobProcessor {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SessionRepository sessionRepository;
    private final ReminderJobRepository reminderJobRepository;
    private final AutomaticContentAssignmentService assignmentService;

    public AssignmentCutoffJobProcessor(
            SessionRepository sessionRepository,
            ReminderJobRepository reminderJobRepository,
            AutomaticContentAssignmentService assignmentService
    ) {
        this.sessionRepository = sessionRepository;
        this.reminderJobRepository = reminderJobRepository;
        this.assignmentService = assignmentService;
    }

    @Transactional
    public boolean process(AssignmentCutoffJobClaimService.Claim claim, Instant now) {
        Optional<LearningSession> sessionResult = sessionRepository.findByIdAndOwnerIdForUpdate(
                claim.sessionId(), LOCAL_PROFILE_ID);
        ReminderJob job = reminderJobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null || !job.isClaimedBy(claim.token())) {
            return false;
        }
        if (sessionResult.isEmpty()) {
            job.cancel();
            reminderJobRepository.saveAndFlush(job);
            return false;
        }

        LearningSession session = sessionResult.get();
        Instant currentCutoff = session.getStartsAt().minus(
                Duration.ofMinutes(session.getAssignmentCutoffMinutesBefore()));
        if (session.getStatus() != SessionStatus.PLANNED
                || !session.getStartsAt().isAfter(now)
                || !job.getScheduledAt().equals(currentCutoff)) {
            job.cancel();
            reminderJobRepository.saveAndFlush(job);
            return false;
        }

        Optional<UUID> assignedContent = assignmentService.assignPendingContent(session.getId());
        job.markProcessed(assignedContent.isPresent()
                ? ReminderJob.Outcome.ASSIGNED
                : ReminderJob.Outcome.NO_ELIGIBLE_CONTENT, now);
        reminderJobRepository.saveAndFlush(job);
        return true;
    }

    @Transactional
    public void recordFailure(AssignmentCutoffJobClaimService.Claim claim, Instant now, String safeError) {
        ReminderJob job = reminderJobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job != null && job.isClaimedBy(claim.token())) {
            job.recordFailure(now, safeError);
            reminderJobRepository.saveAndFlush(job);
        }
    }
}