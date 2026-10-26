package com.openlinklearn.content.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A page of history content and pagination metadata.")
public record HistoryPageResponse(
        @Schema(description = "Completed items on this page")
        List<HistoryItemResponse> items,
        @Schema(description = "Zero-based page index")
        int page,
        @Schema(description = "Requested page size")
        int size,
        @Schema(description = "Total number of matching completed items")
        long totalItems,
        @Schema(description = "Total number of pages")
        int totalPages
) {
}