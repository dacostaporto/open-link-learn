package com.openlinklearn.session.application;

import com.openlinklearn.profile.application.SettingsService;
import com.openlinklearn.profile.application.dto.SettingsResponse;
import com.openlinklearn.session.application.cutoff.AssignmentCutoffJobService;
import com.openlinklearn.session.application.dto.CreateSessionSeriesRequest;
import com.openlinklearn.session.application.dto.SessionSeriesResponse;
import com.openlinklearn.session.application.dto.UpdateSessionSeriesRequest;
import com.openlinklearn.session.application.reminder.ReminderJobService;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionSeries;
import com.openlinklearn.session.domain.SessionSeriesCancelledEvent;
import com.openlinklearn.session.domain.SessionSeriesRepository;
import com.openlinklearn.session.domain.SessionSeriesScheduleChangedEvent;
import com.openlinklearn.session.domain.SessionStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SessionSeriesService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SessionSeriesRepository sessionSeriesRepository;
    private final SettingsService settingsService;
    private final SessionOccurrenceGenerationService occurrenceGenerationService;
    private final SessionRepository sessionRepository;
    private final WeeklyRecurrenceService recurrenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final AssignmentCutoffJobService assignmentCutoffJobService;
    private final ReminderJobService reminderJobService;

    public SessionSeriesService(
            SessionSeriesRepository sessionSeriesRepository,
            SettingsService settingsService,
            SessionOccurrenceGenerationService occurrenceGenerationService,
            SessionRepository sessionRepository,
            WeeklyRecurrenceService recurrenceService,
            ApplicationEventPublisher eventPublisher,
            AssignmentCutoffJobService assignmentCutoffJobService,
            ReminderJobService reminderJobService
    ) {
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.settingsService = settingsService;
        this.occurrenceGenerationService = occurrenceGenerationService;
        this.sessionRepository = sessionRepository;
        this.recurrenceService = recurrenceService;
        this.eventPublisher = eventPublisher;
        this.assignmentCutoffJobService = assignmentCutoffJobService;
        this.reminderJobService = reminderJobService;
    }

    @Transactional
    public SessionSeriesResponse createSeries(CreateSessionSeriesRequest request) {
        validate(request);
        SettingsResponse settings = settingsService.getSettings();
        int reminderMinutes = request.reminderMinutesBefore() == null
                ? settings.defaultReminderMinutesBefore()
                : request.reminderMinutesBefore();
        int assignmentCutoffMinutes = request.assignmentCutoffMinutesBefore() == null
                ? settings.defaultAssignmentCutoffMinutesBefore()
                : request.assignmentCutoffMinutesBefore();
        String timezone = request.timezone().trim();
        List<DayOfWeek> weekdays = request.daysOfWeek().stream()
                .sorted(java.util.Comparator.comparingInt(DayOfWeek::getValue))
                .toList();
        UUID seriesId = UUID.randomUUID();

        SessionSeries series = new SessionSeries(
                seriesId,
                LOCAL_PROFILE_ID,
                request.startDate(),
                request.endDate(),
                request.startTime(),
                request.durationMinutes(),
                timezone,
                weekdays.stream().map(day -> (short) day.getValue()).toArray(Short[]::new),
                reminderMinutes,
                assignmentCutoffMinutes);
        sessionSeriesRepository.save(series);

        Instant generationInstant = Instant.now();
        SessionOccurrenceGenerationService.SeriesGenerationResult generation =
                occurrenceGenerationService.generateThrough(seriesId, generationInstant);

        return toResponse(series, generation.generatedThrough());
        }

        @Transactional(readOnly = true)
        public SessionSeriesResponse getSeries(UUID seriesId) {
        SessionSeries series = sessionSeriesRepository.findByIdAndOwnerId(seriesId, LOCAL_PROFILE_ID)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The session series was not found."));
        int recurrenceHorizonDays = settingsService.getSettings().recurrenceHorizonDays();
        LocalDate generatedThrough = plusDaysSaturated(
            LocalDate.now(ZoneId.of(series.getTimezone())), recurrenceHorizonDays);
        if (series.getEndDate() != null && series.getEndDate().isBefore(generatedThrough)) {
            generatedThrough = series.getEndDate();
        }
        return toResponse(series, generatedThrough);
        }

        @Transactional
        public SessionSeriesResponse updateSeries(UUID seriesId, UpdateSessionSeriesRequest request) {
        if (request == null) {
            throw badRequest("A session series update request is required.");
        }
        if (!"ENTIRE_SERIES".equals(request.applyTo())) {
            String mode = request.applyTo() == null ? "null" : request.applyTo();
            throw badRequest("Unsupported applyTo mode '" + mode + "'. Supported mode: ENTIRE_SERIES.");
        }

        validate(new CreateSessionSeriesRequest(
            request.startDate(),
            request.endDate(),
            request.startTime(),
            request.durationMinutes(),
            request.timezone(),
            request.daysOfWeek(),
            request.reminderMinutesBefore(),
            request.assignmentCutoffMinutesBefore()));

        SessionSeries series = sessionSeriesRepository.findByIdAndOwnerId(seriesId, LOCAL_PROFILE_ID)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The session series was not found."));
        if (!series.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An inactive session series cannot be edited.");
        }

        SettingsResponse settings = settingsService.getSettings();
        int reminderMinutes = request.reminderMinutesBefore() == null
            ? settings.defaultReminderMinutesBefore()
            : request.reminderMinutesBefore();
        int assignmentCutoffMinutes = request.assignmentCutoffMinutesBefore() == null
            ? settings.defaultAssignmentCutoffMinutesBefore()
            : request.assignmentCutoffMinutesBefore();
        String timezone = request.timezone().trim();
        List<DayOfWeek> weekdays = sortedWeekdays(request.daysOfWeek());
        series.updateSchedule(
            request.startDate(),
            request.endDate(),
            request.startTime(),
            request.durationMinutes(),
            timezone,
            weekdays.stream().map(day -> (short) day.getValue()).toArray(Short[]::new),
            reminderMinutes,
            assignmentCutoffMinutes);

        Instant now = Instant.now();
        reconcileFutureOccurrences(series, settings.recurrenceHorizonDays(), reminderMinutes, assignmentCutoffMinutes, now);
        SessionOccurrenceGenerationService.SeriesGenerationResult generation =
            occurrenceGenerationService.generateThrough(seriesId, now);
        eventPublisher.publishEvent(new SessionSeriesScheduleChangedEvent(seriesId));
        return toResponse(series, generation.generatedThrough());
        }

        @Transactional
        public void cancelSeries(UUID seriesId) {
        SessionSeries series = sessionSeriesRepository.findByIdAndOwnerId(seriesId, LOCAL_PROFILE_ID)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The session series was not found."));
        if (!series.isActive()) {
            return;
        }

        Instant now = Instant.now();
        for (LearningSession occurrence : sessionRepository
            .findAllBySeriesIdAndStatusAndStartsAtAfterOrderBySeriesOccurrenceDateAsc(
                seriesId, SessionStatus.PLANNED, now)) {
            occurrence.cancelAndReleaseContent();
            assignmentCutoffJobService.cancelPendingForSession(occurrence.getId());
            reminderJobService.cancelPendingForSession(occurrence.getId());
        }
        series.deactivate();
        eventPublisher.publishEvent(new SessionSeriesCancelledEvent(seriesId));
        }

        private void reconcileFutureOccurrences(
            SessionSeries series,
            int recurrenceHorizonDays,
            int reminderMinutes,
            int assignmentCutoffMinutes,
            Instant now
        ) {
        ZoneId zone = ZoneId.of(series.getTimezone());
        LocalDate localToday = now.atZone(zone).toLocalDate();
        LocalDate throughDate = plusDaysSaturated(localToday, recurrenceHorizonDays);
        if (series.getEndDate() != null && series.getEndDate().isBefore(throughDate)) {
            throughDate = series.getEndDate();
        }
        LocalDate fromDate = series.getStartDate().isAfter(localToday) ? series.getStartDate() : localToday;
        WeeklyRecurrenceService.WeeklyRule rule = new WeeklyRecurrenceService.WeeklyRule(
            series.getStartDate(),
            series.getEndDate(),
            Arrays.stream(series.getDaysOfWeek())
                .map(day -> DayOfWeek.of(day.intValue()))
                .collect(Collectors.toUnmodifiableSet()),
            series.getStartTime(),
            series.getTimezone());
        Map<LocalDate, WeeklyRecurrenceService.Occurrence> desiredOccurrences = fromDate.isAfter(throughDate)
            ? Map.of()
            : recurrenceService.calculate(rule, fromDate, throughDate).stream()
                .collect(Collectors.toMap(WeeklyRecurrenceService.Occurrence::localDate, occurrence -> occurrence));

        for (LearningSession occurrence : sessionRepository
            .findAllBySeriesIdAndStatusAndStartsAtAfterOrderBySeriesOccurrenceDateAsc(
                series.getId(), SessionStatus.PLANNED, now)) {
            WeeklyRecurrenceService.Occurrence desired = desiredOccurrences.get(occurrence.getSeriesOccurrenceDate());
            if (desired == null && !occurrence.getSeriesOccurrenceDate().isBefore(fromDate)) {
                desired = recurrenceService.calculate(
                                rule,
                                occurrence.getSeriesOccurrenceDate(),
                                occurrence.getSeriesOccurrenceDate())
                        .stream()
                        .findFirst()
                        .orElse(null);
            }
            if (desired == null) {
            occurrence.cancelAndReleaseContent();
            assignmentCutoffJobService.cancelPendingForSession(occurrence.getId());
            reminderJobService.cancelPendingForSession(occurrence.getId());
            continue;
            }

            Instant endsAt = desired.startsAt().plus(Duration.ofMinutes(series.getDurationMinutes()));
            boolean reminderScheduleChanged = !occurrence.getStartsAt().equals(desired.startsAt())
                || occurrence.getReminderMinutesBefore() != reminderMinutes;
            boolean changed = !occurrence.getStartsAt().equals(desired.startsAt())
                || !occurrence.getEndsAt().equals(endsAt)
                || !occurrence.getTimezone().equals(series.getTimezone())
                || occurrence.getReminderMinutesBefore() != reminderMinutes
                || occurrence.getAssignmentCutoffMinutesBefore() != assignmentCutoffMinutes;
            if (changed) {
            occurrence.updateSchedule(desired.startsAt(), endsAt, series.getTimezone());
            occurrence.updateReminderMinutesBefore(reminderMinutes);
            occurrence.updateAssignmentCutoffMinutesBefore(assignmentCutoffMinutes);
            assignmentCutoffJobService.refreshForSession(occurrence.getId());
            }
            if (reminderScheduleChanged) {
            reminderJobService.refreshForSession(occurrence.getId());
            }
        }
        }

        private SessionSeriesResponse toResponse(SessionSeries series, LocalDate generatedThrough) {
        List<DayOfWeek> weekdays = Arrays.stream(series.getDaysOfWeek())
            .map(day -> DayOfWeek.of(day.intValue()))
            .sorted(Comparator.comparingInt(DayOfWeek::getValue))
            .toList();
        return new SessionSeriesResponse(
            series.getId(),
            series.getStartDate(),
            series.getEndDate(),
            series.getStartTime(),
            series.getDurationMinutes(),
            series.getTimezone(),
            weekdays,
            series.getReminderMinutesBefore(),
            series.getAssignmentCutoffMinutesBefore(),
            series.isActive(),
            generatedThrough);
        }

        private List<DayOfWeek> sortedWeekdays(List<DayOfWeek> weekdays) {
        return weekdays.stream()
            .sorted(Comparator.comparingInt(DayOfWeek::getValue))
            .toList();
        }

        private LocalDate plusDaysSaturated(LocalDate date, int days) {
        try {
            return date.plusDays(days);
        } catch (DateTimeException exception) {
            return LocalDate.MAX;
        }
    }

    private void validate(CreateSessionSeriesRequest request) {
        if (request == null) {
            throw badRequest("A session series request is required.");
        }
        if (request.startDate() == null || request.startTime() == null) {
            throw badRequest("A series start date and local start time are required.");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw badRequest("The series end date cannot be before its start date.");
        }
        if (request.durationMinutes() == null || request.durationMinutes() < 1 || request.durationMinutes() > 720) {
            throw badRequest("Duration must be between 1 and 720 minutes.");
        }
        if (request.daysOfWeek() == null || request.daysOfWeek().isEmpty()
                || request.daysOfWeek().stream().anyMatch(day -> day == null)
                || Set.copyOf(request.daysOfWeek()).size() != request.daysOfWeek().size()) {
            throw badRequest("Select at least one unique weekday.");
        }
        String timezone = request.timezone() == null ? "" : request.timezone().trim();
        if (timezone.isBlank() || timezone.length() > 64 || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw badRequest("Timezone must be a valid IANA timezone.");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw badRequest("Timezone must be a valid IANA timezone.");
        }
        if (request.reminderMinutesBefore() != null
                && (request.reminderMinutesBefore() < 0 || request.reminderMinutesBefore() > 1440)) {
            throw badRequest("Reminder lead time must be between 0 and 1440 minutes.");
        }
        if (request.assignmentCutoffMinutesBefore() != null
                && (request.assignmentCutoffMinutesBefore() < 0 || request.assignmentCutoffMinutesBefore() > 10080)) {
            throw badRequest("Assignment cutoff must be between 0 and 10080 minutes.");
        }
    }

    private ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}