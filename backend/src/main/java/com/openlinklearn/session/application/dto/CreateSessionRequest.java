package com.openlinklearn.session.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Request to create a one-off planned study session.")
public record CreateSessionRequest(
        @Schema(description = "Required session start instant in ISO-8601 format", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-26T06:30:00Z")
        Instant startsAt,
        @Schema(description = "Session duration in minutes; allowed range is 1-720", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "720", example = "30")
        Integer durationMinutes,
        @Schema(description = "IANA timezone; defaults to the configured profile timezone when omitted", example = "Europe/Madrid")
        String timezone,
        @Schema(description = "Optional pending content item to reserve; it must not be assigned to another active session")
        UUID contentItemId,
        @Schema(description = "Reminder lead time in minutes; allowed range is 0-1440, defaults from settings", minimum = "0", maximum = "1440", example = "10")
        Integer reminderMinutesBefore,
        @Schema(description = "Assignment cutoff in minutes before start; allowed range is 0-10080, defaults from settings", minimum = "0", maximum = "10080", example = "30")
        Integer assignmentCutoffMinutesBefore
) {
}