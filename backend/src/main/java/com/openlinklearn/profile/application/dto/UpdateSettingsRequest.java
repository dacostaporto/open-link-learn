package com.openlinklearn.profile.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAnySetter;

@Schema(description = "Complete set of profile defaults to replace; existing sessions and series are not changed.")
public record UpdateSettingsRequest(
                @Schema(description = "IANA timezone used as the default for new sessions and series", example = "Europe/Madrid", maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
        String timezone,
                @Schema(description = "Default reminder lead time in minutes", minimum = "0", maximum = "1440", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer defaultReminderMinutesBefore,
                @Schema(description = "Default assignment cutoff before a session in minutes", minimum = "0", maximum = "10080", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer defaultAssignmentCutoffMinutesBefore,
                @Schema(description = "Rolling generation horizon in days", minimum = "7", maximum = "365", example = "90", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer recurrenceHorizonDays
) {
        @JsonAnySetter
        public void rejectUnsupportedProperty(String name, Object value) {
                throw new IllegalArgumentException("Unsupported settings property: " + name);
        }
}