package com.openlinklearn.session.application.dto;

import com.openlinklearn.session.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "One-off session returned by the sessions API.")
public record SessionResponse(
        @Schema(description = "Session identifier")
        UUID id,
        @Schema(description = "Recurring-series identifier; null for one-off sessions")
        UUID seriesId,
        @Schema(description = "Session start instant in UTC")
        Instant startsAt,
        @Schema(description = "Session end instant in UTC")
        Instant endsAt,
        @Schema(description = "IANA timezone associated with the session")
        String timezone,
        @Schema(description = "Reserved content, if any")
        SessionContentResponse content,
        @Schema(description = "Session lifecycle status")
        SessionStatus status,
        @Schema(description = "Reminder lead time in minutes")
        int reminderMinutesBefore,
        @Schema(description = "Assignment cutoff in minutes before the session")
        int assignmentCutoffMinutesBefore,
        @Schema(description = "Completion time in UTC, or null unless completed")
        Instant completedAt
) {
}