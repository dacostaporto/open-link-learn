package com.openlinklearn.session.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Request to create a weekly recurring session series.")
public record CreateSessionSeriesRequest(
        @Schema(description = "First eligible local occurrence date", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-26")
        LocalDate startDate,
        @Schema(description = "Optional inclusive last local occurrence date", example = "2027-01-24")
        LocalDate endDate,
        @Schema(description = "Series local wall-clock start time", requiredMode = Schema.RequiredMode.REQUIRED, example = "07:30:00")
        LocalTime startTime,
        @Schema(description = "Occurrence duration in minutes; allowed range is 1-720", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "720", example = "30")
        Integer durationMinutes,
        @Schema(description = "Required IANA timezone for the series", requiredMode = Schema.RequiredMode.REQUIRED, example = "Europe/Madrid")
        String timezone,
        @Schema(description = "Selected ISO weekdays, Monday through Sunday", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"MONDAY\",\"WEDNESDAY\"]")
        List<DayOfWeek> daysOfWeek,
        @Schema(description = "Reminder lead minutes; defaults are persisted at creation", minimum = "0", maximum = "1440", example = "10")
        Integer reminderMinutesBefore,
        @Schema(description = "Assignment cutoff lead minutes; defaults are persisted at creation", minimum = "0", maximum = "10080", example = "30")
        Integer assignmentCutoffMinutesBefore
) { }