package com.openlinklearn.profile.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current local owner settings. These settings are currently read-only through the API.")
public record SettingsResponse(
        @Schema(description = "Configured IANA timezone", example = "Etc/UTC")
        String timezone,
        @Schema(description = "Default reminder lead time in minutes")
        int defaultReminderMinutesBefore,
        @Schema(description = "Default assignment cutoff in minutes")
        int defaultAssignmentCutoffMinutesBefore,
        @Schema(description = "Configured recurrence generation horizon in days")
        int recurrenceHorizonDays
) {
}