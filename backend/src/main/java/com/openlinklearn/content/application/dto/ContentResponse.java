package com.openlinklearn.content.application.dto;

import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.content.domain.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Content item returned by the backlog API.")
public record ContentResponse(
        @Schema(description = "Content item identifier")
        UUID id,
        @Schema(description = "Submitted content URL", example = "https://www.youtube.com/watch?v=jYUZAF3ePFE")
        String url,
        @Schema(description = "Conservatively normalized URL used for duplicate detection")
        String normalizedUrl,
        @Schema(description = "Display title")
        String title,
        @Schema(description = "Detected supported content type")
        ContentType type,
        @Schema(description = "Thumbnail URL when available")
        String thumbnailUrl,
        @Schema(description = "Source name when available")
        String sourceName,
        @Schema(description = "Actual source duration in seconds when available")
        Integer durationSeconds,
        @Schema(description = "Estimated duration in minutes, if known")
        Integer estimatedDurationMinutes,
        @Schema(description = "Content lifecycle status")
        ContentStatus status,
        @Schema(description = "Assigned active session identifier, if any")
        UUID assignedSessionId,
        @Schema(description = "Creation time in UTC")
        Instant createdAt,
        @Schema(description = "Completion time in UTC, or null while pending")
        Instant completedAt
) {

    public ContentResponse(
            UUID id,
            String url,
            String normalizedUrl,
            String title,
            ContentType type,
            String thumbnailUrl,
            String sourceName,
            Integer estimatedDurationMinutes,
            ContentStatus status,
            UUID assignedSessionId,
            Instant createdAt,
            Instant completedAt
    ) {
        this(id, url, normalizedUrl, title, type, thumbnailUrl, sourceName, null,
                estimatedDurationMinutes, status, assignedSessionId, createdAt, completedAt);
    }
}