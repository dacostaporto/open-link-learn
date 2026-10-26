package com.openlinklearn.session.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Recurring session series and the inclusive date through which occurrences were generated.")
public record SessionSeriesResponse(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        int durationMinutes,
        String timezone,
        List<DayOfWeek> daysOfWeek,
        int reminderMinutesBefore,
        int assignmentCutoffMinutesBefore,
        boolean active,
        LocalDate generatedThrough
) { }