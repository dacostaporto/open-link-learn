package com.openlinklearn.content.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A page of content items and pagination metadata.")
public record ContentPageResponse(
        @Schema(description = "Items on this page")
        List<ContentResponse> items,
        @Schema(description = "Zero-based page index")
        int page,
        @Schema(description = "Requested page size")
        int size,
        @Schema(description = "Total number of matching items")
        long totalItems,
        @Schema(description = "Total number of pages")
        int totalPages
) {
}