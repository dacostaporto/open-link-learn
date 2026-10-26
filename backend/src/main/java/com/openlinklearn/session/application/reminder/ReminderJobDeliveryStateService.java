package com.openlinklearn.session.application.reminder;

import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.ReminderJob;
import com.openlinklearn.session.domain.ReminderJobRepository;
import com.openlinklearn.session.domain.ReminderSender;
import com.openlinklearn.session.domain.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class ReminderJobDeliveryStateService {

    private final ReminderJobRepository reminderJobRepository;
    private final SessionRepository sessionRepository;

    public ReminderJobDeliveryStateService(
            ReminderJobRepository reminderJobRepository,
            SessionRepository sessionRepository
    ) {
        this.reminderJobRepository = reminderJobRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Optional<ReminderSender.ReminderDelivery> getDelivery(ReminderJobClaimService.Claim claim) {
        ReminderJob job = reminderJobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null || !job.isClaimedBy(claim.token())) {
            return Optional.empty();
        }
        LearningSession session = sessionRepository.findByIdAndOwnerId(job.getSessionId(), job.getOwnerId())
            .orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(new ReminderSender.ReminderDelivery(
            session.getOwnerId(),
            session.getId(),
            session.getStartsAt(),
            session.getReminderMinutesBefore()
        ));
    }

    @Transactional
    public boolean markSent(ReminderJobClaimService.Claim claim, Instant now) {
        ReminderJob job = reminderJobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null || !job.isClaimedBy(claim.token())) {
            return false;
        }
        job.markSent(now);
        reminderJobRepository.saveAndFlush(job);
        return true;
    }

    @Transactional
    public boolean recordFailure(
            ReminderJobClaimService.Claim claim,
            Instant now,
            String safeError,
            boolean retryable
    ) {
        ReminderJob job = reminderJobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null || !job.isClaimedBy(claim.token())) {
            return false;
        }
        if (retryable) {
            job.recordFailure(now, safeError);
        } else {
            job.markPermanentlyFailed(safeError);
        }
        reminderJobRepository.saveAndFlush(job);
        return true;
    }
}