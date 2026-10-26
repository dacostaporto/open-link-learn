package com.openlinklearn.session.application;

import com.openlinklearn.profile.domain.ProfileSettings;
import com.openlinklearn.profile.domain.ProfileSettingsRepository;
import com.openlinklearn.session.application.cutoff.AssignmentCutoffJobService;
import com.openlinklearn.session.application.reminder.ReminderJobService;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionSeries;
import com.openlinklearn.session.domain.SessionSeriesRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SessionOccurrenceGenerationService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SessionSeriesRepository sessionSeriesRepository;
    private final SessionRepository sessionRepository;
    private final ProfileSettingsRepository profileSettingsRepository;
    private final WeeklyRecurrenceService recurrenceService;
    private final AssignmentCutoffJobService assignmentCutoffJobService;
    private final ReminderJobService reminderJobService;

    public SessionOccurrenceGenerationService(
            SessionSeriesRepository sessionSeriesRepository,
            SessionRepository sessionRepository,
            ProfileSettingsRepository profileSettingsRepository,
                WeeklyRecurrenceService recurrenceService,
                AssignmentCutoffJobService assignmentCutoffJobService,
                ReminderJobService reminderJobService
    ) {
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.sessionRepository = sessionRepository;
        this.profileSettingsRepository = profileSettingsRepository;
        this.recurrenceService = recurrenceService;
        this.assignmentCutoffJobService = assignmentCutoffJobService;
        this.reminderJobService = reminderJobService;
    }

    /** Runs daily; database uniqueness makes overlapping application instances safe. */
    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @Transactional
    public void replenishActiveSeries() {
        generateThrough(Instant.now());
    }

    /** Generates through the inclusive local-date boundary of today plus the configured horizon. */
    @Transactional
    public GenerationResult generateThrough(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("The generation reference instant is required.");
        }

        GenerationSettings settings = loadSettings();
        List<SessionSeries> activeSeries = sessionSeriesRepository.findAllByActiveTrueOrderByIdAsc();

        int inserted = 0;
        for (SessionSeries series : activeSeries) {
            inserted += generateForSeries(series, settings, now).occurrencesInserted();
        }
        return new GenerationResult(activeSeries.size(), inserted);
    }

    @Transactional
    public SeriesGenerationResult generateThrough(UUID seriesId, Instant now) {
        if (seriesId == null || now == null) {
            throw new IllegalArgumentException("A series ID and generation reference instant are required.");
        }

        GenerationSettings settings = loadSettings();
        SessionSeries series = sessionSeriesRepository.findByIdAndOwnerIdAndActiveTrue(seriesId, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new IllegalStateException("The active session series was not found."));
        return generateForSeries(series, settings, now);
    }

    private SeriesGenerationResult generateForSeries(SessionSeries series, GenerationSettings settings, Instant now) {
        ZoneId zone = ZoneId.of(series.getTimezone());
        LocalDate localToday = now.atZone(zone).toLocalDate();
        LocalDate throughDate = plusDaysSaturated(localToday, settings.recurrenceHorizonDays());
        if (series.getEndDate() != null && series.getEndDate().isBefore(throughDate)) {
            throughDate = series.getEndDate();
        }
        LocalDate fromDate = series.getStartDate().isAfter(localToday) ? series.getStartDate() : localToday;
        if (fromDate.isAfter(throughDate)) {
            return new SeriesGenerationResult(0, throughDate);
        }

        List<WeeklyRecurrenceService.Occurrence> occurrences = recurrenceService.calculate(
                new WeeklyRecurrenceService.WeeklyRule(
                        series.getStartDate(),
                        series.getEndDate(),
                        Arrays.stream(series.getDaysOfWeek())
                                .map(day -> DayOfWeek.of(day.intValue()))
                                .collect(Collectors.toUnmodifiableSet()),
                        series.getStartTime(),
                        series.getTimezone()),
                fromDate,
                throughDate);

        int inserted = 0;
        for (WeeklyRecurrenceService.Occurrence occurrence : occurrences) {
            inserted += insertOccurrence(series, settings, occurrence, now);
        }
        return new SeriesGenerationResult(inserted, throughDate);
    }

    private int insertOccurrence(
            SessionSeries series,
            GenerationSettings settings,
            WeeklyRecurrenceService.Occurrence occurrence,
            Instant now
    ) {
        Instant startsAt = occurrence.startsAt();
        Instant endsAt = startsAt.plus(Duration.ofMinutes(series.getDurationMinutes()));
        int reminderMinutes = series.getReminderMinutesBefore() == null
                ? settings.defaultReminderMinutesBefore()
                : series.getReminderMinutesBefore();
        int assignmentCutoffMinutes = series.getAssignmentCutoffMinutesBefore() == null
                ? settings.defaultAssignmentCutoffMinutesBefore()
                : series.getAssignmentCutoffMinutesBefore();

        int inserted = sessionRepository.insertOccurrenceIfAbsent(
                series.getOwnerId(),
                startsAt,
                endsAt,
                series.getTimezone(),
                reminderMinutes,
                assignmentCutoffMinutes,
                series.getId(),
                occurrence.localDate());
            if (inserted > 0) {
                LearningSession session = sessionRepository.findBySeriesIdAndSeriesOccurrenceDate(
                        series.getId(), occurrence.localDate())
                    .orElseThrow(() -> new IllegalStateException("The generated session occurrence was not found."));
                assignmentCutoffJobService.scheduleForCreatedSession(session.getId(), now);
                reminderJobService.scheduleForCreatedSession(session.getId());
            }
            return inserted;
    }

    private GenerationSettings loadSettings() {
        ProfileSettings profileSettings = profileSettingsRepository.findById(LOCAL_PROFILE_ID)
            .orElseThrow(() -> new IllegalStateException(
                "The local profile is missing; verify that Flyway migrations have run."));
        return new GenerationSettings(
            profileSettings.getDefaultReminderMinutesBefore(),
            profileSettings.getDefaultAssignmentCutoffMinutesBefore(),
            profileSettings.getRecurrenceHorizonDays());
    }

    private LocalDate plusDaysSaturated(LocalDate date, int days) {
        try {
            return date.plusDays(days);
        } catch (DateTimeException exception) {
            return LocalDate.MAX;
        }
    }

    public record GenerationResult(int activeSeriesChecked, int occurrencesInserted) { }

    public record SeriesGenerationResult(int occurrencesInserted, LocalDate generatedThrough) { }

    private record GenerationSettings(
            int defaultReminderMinutesBefore,
            int defaultAssignmentCutoffMinutesBefore,
            int recurrenceHorizonDays
    ) { }

}