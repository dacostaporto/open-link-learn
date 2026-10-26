package com.openlinklearn.content.application.dto;

import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.content.domain.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A completed content item in the history.")
public record HistoryItemResponse(
        @Schema(description = "Content identifier")
        UUID id,
        @Schema(description = "Original content URL")
        String url,
        @Schema(description = "Content title")
        String title,
        @Schema(description = "Content type")
        ContentType type,
        @Schema(description = "Lifecycle status; history items are always COMPLETED")
        ContentStatus status,
        @Schema(description = "Completion timestamp in UTC", format = "date-time")
        Instant completedAt
) {
}