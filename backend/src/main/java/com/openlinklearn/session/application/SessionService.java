package com.openlinklearn.session.application;

import com.openlinklearn.content.domain.ContentItem;
import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.profile.application.SettingsService;
import com.openlinklearn.profile.application.dto.SettingsResponse;
import com.openlinklearn.session.application.cutoff.AssignmentCutoffJobService;
import com.openlinklearn.session.application.dto.CreateSessionRequest;
import com.openlinklearn.session.application.dto.SessionContentResponse;
import com.openlinklearn.session.application.dto.SessionListResponse;
import com.openlinklearn.session.application.dto.SessionResponse;
import com.openlinklearn.session.application.dto.UpdateSessionRequest;
import com.openlinklearn.session.application.reminder.ReminderJobService;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SessionService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Set<SessionStatus> ACTIVE_STATUSES = Set.of(SessionStatus.PLANNED, SessionStatus.IN_PROGRESS);

    private final SessionRepository sessionRepository;
    private final ContentRepository contentRepository;
    private final SettingsService settingsService;
    private final AssignmentCutoffJobService assignmentCutoffJobService;
    private final ReminderJobService reminderJobService;

    public SessionService(
            SessionRepository sessionRepository,
            ContentRepository contentRepository,
            SettingsService settingsService,
            AssignmentCutoffJobService assignmentCutoffJobService,
            ReminderJobService reminderJobService
    ) {
        this.sessionRepository = sessionRepository;
        this.contentRepository = contentRepository;
        this.settingsService = settingsService;
        this.assignmentCutoffJobService = assignmentCutoffJobService;
        this.reminderJobService = reminderJobService;
    }

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        if (request.startsAt() == null) {
            throw badRequest("A session start time is required.");
        }
        if (request.durationMinutes() == null || request.durationMinutes() < 1 || request.durationMinutes() > 720) {
            throw badRequest("Duration must be between 1 and 720 minutes.");
        }

        SettingsResponse settings = settingsService.getSettings();
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? settings.timezone()
                : request.timezone().trim();
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw badRequest("Timezone must be a valid IANA timezone.");
        }

        int reminder = request.reminderMinutesBefore() == null
                ? settings.defaultReminderMinutesBefore()
                : request.reminderMinutesBefore();
        int cutoff = request.assignmentCutoffMinutesBefore() == null
                ? settings.defaultAssignmentCutoffMinutesBefore()
                : request.assignmentCutoffMinutesBefore();
        if (reminder < 0 || reminder > 1440) {
            throw badRequest("Reminder lead time must be between 0 and 1440 minutes.");
        }
        if (cutoff < 0 || cutoff > 10080) {
            throw badRequest("Assignment cutoff must be between 0 and 10080 minutes.");
        }

        ContentItem contentItem = request.contentItemId() == null
                ? null
                : findEligibleContent(request.contentItemId());
        Instant endsAt = request.startsAt().plus(Duration.ofMinutes(request.durationMinutes()));
        LearningSession session = new LearningSession(
                LOCAL_PROFILE_ID,
                request.startsAt(),
                endsAt,
                timezone,
                contentItem,
                reminder,
                cutoff
        );
        LearningSession saved;
        try {
            saved = sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Content is already assigned to an active session.");
        }
        assignmentCutoffJobService.scheduleForCreatedSession(saved.getId(), Instant.now());
        reminderJobService.scheduleForCreatedSession(saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public SessionResponse updateSession(UUID sessionId, UpdateSessionRequest request) {
        if (request == null || !request.hasChanges()) {
            throw badRequest("At least one session field must be provided.");
        }

        LearningSession session = sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session was not found."));
        if (session.getStatus() != SessionStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only planned sessions can be updated.");
        }

        if (request.hasStartsAt() && request.getStartsAt() == null) {
            throw badRequest("A session start time cannot be null.");
        }
        if (request.hasDurationMinutes() && (request.getDurationMinutes() == null
                || request.getDurationMinutes() < 1 || request.getDurationMinutes() > 720)) {
            throw badRequest("Duration must be between 1 and 720 minutes.");
        }
        if (request.hasReminderMinutesBefore() && (request.getReminderMinutesBefore() == null
                || request.getReminderMinutesBefore() < 0 || request.getReminderMinutesBefore() > 1440)) {
            throw badRequest("Reminder lead time must be between 0 and 1440 minutes.");
        }
        if (request.hasAssignmentCutoffMinutesBefore() && (request.getAssignmentCutoffMinutesBefore() == null
                || request.getAssignmentCutoffMinutesBefore() < 0
                || request.getAssignmentCutoffMinutesBefore() > 10080)) {
            throw badRequest("Assignment cutoff must be between 0 and 10080 minutes.");
        }

        Instant previousStartsAt = session.getStartsAt();
        int previousReminderMinutes = session.getReminderMinutesBefore();
        int previousCutoffMinutes = session.getAssignmentCutoffMinutesBefore();
        Instant startsAt = request.hasStartsAt() ? request.getStartsAt() : previousStartsAt;
        Duration duration = request.hasDurationMinutes()
                ? Duration.ofMinutes(request.getDurationMinutes())
                : Duration.between(session.getStartsAt(), session.getEndsAt());
        if (duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofMinutes(720)) > 0) {
            throw badRequest("Duration must be between 1 and 720 minutes.");
        }

        Instant endsAt;
        try {
            endsAt = startsAt.plus(duration);
        } catch (DateTimeException | ArithmeticException exception) {
            throw badRequest("The session time range is invalid.");
        }

        ContentItem contentItem = session.getContentItem();
        if (request.hasContentItemId()) {
            UUID requestedContentId = request.getContentItemId();
            contentItem = requestedContentId == null
                    ? null
                    : contentItem != null && contentItem.getId().equals(requestedContentId)
                            ? contentItem
                            : findEligibleContent(requestedContentId);
        }

        session.updateSchedule(startsAt, endsAt);
        if (request.hasContentItemId()) {
            session.updateContentItem(contentItem);
        }
        if (request.hasReminderMinutesBefore()) {
            session.updateReminderMinutesBefore(request.getReminderMinutesBefore());
        }
        if (request.hasAssignmentCutoffMinutesBefore()) {
            session.updateAssignmentCutoffMinutesBefore(request.getAssignmentCutoffMinutesBefore());
        }

        LearningSession saved;
        try {
            saved = sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Content is already assigned to an active session.");
        }
        if (!previousStartsAt.equals(saved.getStartsAt())
                || previousCutoffMinutes != saved.getAssignmentCutoffMinutesBefore()) {
            assignmentCutoffJobService.refreshForSession(sessionId);
        }
        if (!previousStartsAt.equals(saved.getStartsAt())
                || previousReminderMinutes != saved.getReminderMinutesBefore()) {
            reminderJobService.refreshForSession(sessionId);
        }
        return toResponse(saved);
    }

    @Transactional
    public SessionResponse completeSession(UUID sessionId) {
        LearningSession session = sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session was not found."));
        if (session.getStatus() == SessionStatus.COMPLETED) {
            assignmentCutoffJobService.cancelPendingForSession(sessionId);
            reminderJobService.cancelPendingForSession(sessionId);
            return toResponse(session);
        }
        if (session.getStatus() != SessionStatus.PLANNED && session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This session cannot be completed from its current state.");
        }

        ContentItem content = session.getContentItem();
        if (content != null) {
            content = contentRepository.findByIdAndOwnerIdForUpdate(content.getId(), LOCAL_PROFILE_ID)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned content was not found."));
        }

        Instant completedAt = Instant.now();
        if (content != null) {
            content.markCompleted(completedAt);
            contentRepository.saveAndFlush(content);
        }
        session.markCompleted(completedAt);
        sessionRepository.saveAndFlush(session);
        assignmentCutoffJobService.cancelPendingForSession(sessionId);
        reminderJobService.cancelPendingForSession(sessionId);
        return toResponse(session);
    }

    @Transactional
    public SessionResponse skipSession(UUID sessionId) {
        return endSession(sessionId, SessionStatus.SKIPPED);
    }

    @Transactional
    public SessionResponse cancelSession(UUID sessionId) {
        return endSession(sessionId, SessionStatus.CANCELLED);
    }

    private SessionResponse endSession(UUID sessionId, SessionStatus targetStatus) {
        LearningSession session = sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session was not found."));
        if (session.getStatus() == targetStatus) {
            assignmentCutoffJobService.cancelPendingForSession(sessionId);
            reminderJobService.cancelPendingForSession(sessionId);
            return toResponse(session);
        }
        boolean validTransition = targetStatus == SessionStatus.SKIPPED
            ? session.getStatus() == SessionStatus.PLANNED || session.getStatus() == SessionStatus.IN_PROGRESS
            : session.getStatus() == SessionStatus.PLANNED;
        if (!validTransition) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This session cannot transition from its current state.");
        }

        if (targetStatus == SessionStatus.SKIPPED) {
            session.skipAndReleaseContent();
        } else {
            session.cancelAndReleaseContent();
        }
        LearningSession saved = sessionRepository.saveAndFlush(session);
        assignmentCutoffJobService.cancelPendingForSession(sessionId);
        reminderJobService.cancelPendingForSession(sessionId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SessionListResponse getSessions(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw badRequest("A valid time range with from before to is required.");
        }
        List<SessionResponse> items = sessionRepository
            .findOverlappingByOwner(
                        LOCAL_PROFILE_ID,
                        from,
                        to
                )
                .stream()
                .map(this::toResponse)
                .toList();
        return new SessionListResponse(items);
    }

    private ContentItem findEligibleContent(UUID contentItemId) {
        ContentItem content = contentRepository.findByIdAndOwnerIdForUpdate(contentItemId, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found."));
        if (content.getStatus() != ContentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending content can be assigned.");
        }
        if (sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(
                LOCAL_PROFILE_ID,
                contentItemId,
                ACTIVE_STATUSES
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Content is already assigned to an active session.");
        }
        return content;
    }

    private SessionResponse toResponse(LearningSession session) {
        ContentItem content = session.getContentItem();
        SessionContentResponse contentResponse = content == null
                ? null
                : new SessionContentResponse(content.getId(), content.getTitle(), content.getUrl(), content.getContentType());
        return new SessionResponse(
                session.getId(),
            session.getSeriesId(),
                session.getStartsAt(),
                session.getEndsAt(),
                session.getTimezone(),
                contentResponse,
                session.getStatus(),
                session.getReminderMinutesBefore(),
                session.getAssignmentCutoffMinutesBefore(),
                session.getCompletedAt()
        );
    }

    private ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}