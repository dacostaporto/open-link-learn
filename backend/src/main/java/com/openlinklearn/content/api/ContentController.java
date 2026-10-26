package com.openlinklearn.content.api;

import com.openlinklearn.common.ApiProblem;
import com.openlinklearn.content.application.ContentService;
import com.openlinklearn.content.application.dto.ContentPageResponse;
import com.openlinklearn.content.application.dto.ContentResponse;
import com.openlinklearn.content.application.dto.CreateContentRequest;
import com.openlinklearn.content.application.dto.UpdateContentRequest;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.content.domain.ContentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content")
@Tag(name = "Content", description = "Backlog content management")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping
        @Operation(summary = "List content", description = "Returns owner-scoped content with optional filters and pagination.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Content page returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
        })
    public ContentPageResponse getContent(
            @Parameter(description = "Filter by lifecycle status") @RequestParam(required = false) ContentStatus status,
            @Parameter(description = "Filter by content type") @RequestParam(required = false) ContentType type,
            @Parameter(description = "Case-insensitive title search") @RequestParam(required = false) String search,
            @Parameter(description = "Zero-based page index", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, from 1 to 100", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return contentService.getContent(status, type, search, page, size);
    }

    @GetMapping("/{id}")
        @Operation(summary = "Get content", description = "Returns one content item in the local owner context.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Content returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Content was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
        })
    public ContentResponse getContentById(@PathVariable UUID id) {
        return contentService.getContent(id);
    }

    @PatchMapping("/{id}")
        @Operation(summary = "Update content details", description = "Updates the title and/or estimated duration of pending content. Unknown and immutable fields are rejected.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Content updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Update must include a valid title and/or a positive estimated duration", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "404", description = "Content was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Only pending content can be edited", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
        })
    public ContentResponse updateContent(@PathVariable UUID id, @RequestBody UpdateContentRequest request) {
        return contentService.updateContent(id, request);
    }

    @PostMapping("/{id}/complete")
        @Operation(summary = "Complete content", description = "Marks unassigned content completed. Content assigned to an active session must be completed through that session.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Content completed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Content was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Content is assigned to an active session", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
        })
    public ContentResponse completeContent(@PathVariable UUID id) {
        return contentService.completeContent(id);
    }

    @DeleteMapping("/{id}")
        @Operation(summary = "Delete content", description = "Deletes pending, unassigned content.")
        @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Content deleted"),
            @ApiResponse(responseCode = "404", description = "Content was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Content is completed or assigned to an active session", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
        })
    public ResponseEntity<Void> deleteContent(@PathVariable UUID id) {
        contentService.deleteContent(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
        @Operation(summary = "Add content", description = "Validates a supported HTTP(S) URL and attempts best-effort YouTube oEmbed or article metadata retrieval, using fallback metadata when lookup fails.")
        @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Content created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentResponse.class))),
            @ApiResponse(responseCode = "400", description = "URL is invalid or unsafe", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Normalized URL already exists", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
        })
    public ResponseEntity<ContentResponse> createContent(@RequestBody CreateContentRequest request) {
        ContentResponse response = contentService.createContent(request);
        return ResponseEntity.created(URI.create("/api/v1/content/" + response.id())).body(response);
    }
}