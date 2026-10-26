package com.openlinklearn.content.api;

import com.openlinklearn.common.ApiProblem;
import com.openlinklearn.content.application.ContentService;
import com.openlinklearn.content.application.dto.HistoryPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
@Tag(name = "History", description = "Completed content history")
public class HistoryController {

    private final ContentService contentService;

    public HistoryController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping
    @Operation(summary = "List history content", description = "Returns owner-scoped completed content, newest completion first, with stable pagination and optional case-insensitive title/source search.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "History page returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HistoryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public HistoryPageResponse getHistory(
            @Parameter(description = "Case-insensitive substring search across title and source name") @RequestParam(required = false) String search,
            @Parameter(description = "Zero-based page index", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, from 1 to 100", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return contentService.getHistory(search, page, size);
    }
}