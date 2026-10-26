package com.openlinklearn.session.application.cutoff;

import com.openlinklearn.session.application.AutomaticContentAssignmentService;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.ReminderJob;
import com.openlinklearn.session.domain.ReminderJobRepository;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AssignmentCutoffJobService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SessionRepository sessionRepository;
    private final ReminderJobRepository reminderJobRepository;
    private final AutomaticContentAssignmentService assignmentService;

    public AssignmentCutoffJobService(
            SessionRepository sessionRepository,
            ReminderJobRepository reminderJobRepository,
            AutomaticContentAssignmentService assignmentService
    ) {
        this.sessionRepository = sessionRepository;
        this.reminderJobRepository = reminderJobRepository;
        this.assignmentService = assignmentService;
    }

    @Transactional
    public void scheduleForCreatedSession(UUID sessionId, Instant now) {
        LearningSession session = lockSession(sessionId);
        if (session.getStatus() != SessionStatus.PLANNED) {
            return;
        }

        Instant scheduledAt = scheduledAt(session);
        if (reminderJobRepository.existsBySessionIdAndKindAndScheduledAt(
                sessionId, ReminderJob.Kind.ASSIGNMENT_CUTOFF, scheduledAt)) {
            return;
        }

        ReminderJob job = ensurePendingJob(session);
        if (now.isAfter(job.getScheduledAt())) {
            if (session.getStartsAt().isAfter(now) && session.getStatus() == SessionStatus.PLANNED) {
                job.markProcessed(assignmentService.assignPendingContent(sessionId).isPresent()
                        ? ReminderJob.Outcome.ASSIGNED
                        : ReminderJob.Outcome.NO_ELIGIBLE_CONTENT, now);
            } else {
                job.cancel();
            }
            reminderJobRepository.saveAndFlush(job);
        }
    }

    @Transactional
    public void refreshForSession(UUID sessionId) {
        LearningSession session = lockSession(sessionId);
        if (session.getStatus() != SessionStatus.PLANNED) {
            cancelPendingJobs(sessionId);
            return;
        }
        ensurePendingJob(session);
    }

    @Transactional
    public void cancelPendingForSession(UUID sessionId) {
        cancelPendingJobs(sessionId);
    }

    private ReminderJob ensurePendingJob(LearningSession session) {
        Instant scheduledAt = scheduledAt(session);
        List<ReminderJob> openJobs = reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                session.getId(),
                ReminderJob.Kind.ASSIGNMENT_CUTOFF,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING));
        ReminderJob current = openJobs.stream()
                .filter(job -> job.getScheduledAt().equals(scheduledAt))
                .findFirst()
                .orElse(null);
        for (ReminderJob job : openJobs) {
            if (job != current) {
                job.cancel();
                reminderJobRepository.saveAndFlush(job);
            }
        }
        if (current != null) {
            return current;
        }

        ReminderJob replacement = new ReminderJob(
                session.getOwnerId(),
                session.getId(),
                ReminderJob.Kind.ASSIGNMENT_CUTOFF,
                scheduledAt,
                "assignment-cutoff:" + session.getId() + ":" + UUID.randomUUID());
        return reminderJobRepository.saveAndFlush(replacement);
    }

    private Instant scheduledAt(LearningSession session) {
        return session.getStartsAt().minus(Duration.ofMinutes(session.getAssignmentCutoffMinutesBefore()));
    }

    private void cancelPendingJobs(UUID sessionId) {
        for (ReminderJob job : reminderJobRepository.findBySessionIdAndKindAndStatusIn(
            sessionId,
            ReminderJob.Kind.ASSIGNMENT_CUTOFF,
            Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING))) {
            job.cancel();
            reminderJobRepository.saveAndFlush(job);
        }
    }

    private LearningSession lockSession(UUID sessionId) {
        return sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session was not found."));
    }
}