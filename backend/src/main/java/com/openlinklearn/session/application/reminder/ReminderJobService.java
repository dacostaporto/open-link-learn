package com.openlinklearn.session.application.reminder;

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
public class ReminderJobService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SessionRepository sessionRepository;
    private final ReminderJobRepository reminderJobRepository;

    public ReminderJobService(
            SessionRepository sessionRepository,
            ReminderJobRepository reminderJobRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.reminderJobRepository = reminderJobRepository;
    }

    @Transactional
    public void scheduleForCreatedSession(UUID sessionId) {
        refreshForSession(sessionId);
    }

    @Transactional
    public void refreshForSession(UUID sessionId) {
        LearningSession session = lockSession(sessionId);
        if (session.getStatus() != SessionStatus.PLANNED) {
            cancelOpenJobs(sessionId);
            return;
        }

        Instant scheduledAt = scheduledAt(session);
        List<ReminderJob> openJobs = openJobs(sessionId);
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
        if (current == null) {
            reminderJobRepository.saveAndFlush(new ReminderJob(
                    session.getOwnerId(),
                    session.getId(),
                    ReminderJob.Kind.REMINDER,
                    scheduledAt,
                    "reminder:" + session.getId() + ":" + UUID.randomUUID()));
        }
    }

    @Transactional
    public void cancelPendingForSession(UUID sessionId) {
        cancelOpenJobs(sessionId);
    }

    private Instant scheduledAt(LearningSession session) {
        return session.getStartsAt().minus(Duration.ofMinutes(session.getReminderMinutesBefore()));
    }

    private List<ReminderJob> openJobs(UUID sessionId) {
        return reminderJobRepository.findBySessionIdAndKindAndStatusIn(
                sessionId,
                ReminderJob.Kind.REMINDER,
                Set.of(ReminderJob.Status.PENDING, ReminderJob.Status.PROCESSING));
    }

    private void cancelOpenJobs(UUID sessionId) {
        for (ReminderJob job : openJobs(sessionId)) {
            job.cancel();
            reminderJobRepository.saveAndFlush(job);
        }
    }

    private LearningSession lockSession(UUID sessionId) {
        return sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session was not found."));
    }
}