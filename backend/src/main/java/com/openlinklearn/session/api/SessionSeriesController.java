package com.openlinklearn.session.api;

import com.openlinklearn.common.ApiProblem;
import com.openlinklearn.session.application.SessionSeriesService;
import com.openlinklearn.session.application.dto.CreateSessionSeriesRequest;
import com.openlinklearn.session.application.dto.SessionSeriesResponse;
import com.openlinklearn.session.application.dto.UpdateSessionSeriesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/session-series")
@Tag(name = "Session series", description = "Weekly recurring session schedules")
public class SessionSeriesController {

    private final SessionSeriesService sessionSeriesService;

    public SessionSeriesController(SessionSeriesService sessionSeriesService) {
        this.sessionSeriesService = sessionSeriesService;
    }

    @PostMapping
    @Operation(summary = "Create a weekly session series", description = "Creates an owner-scoped series, materializes configured reminder/cutoff defaults, and generates occurrences through the inclusive horizon.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Series created with its initial occurrence horizon", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionSeriesResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid series dates, weekdays, timezone, duration, reminder, or cutoff", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<SessionSeriesResponse> createSeries(@RequestBody CreateSessionSeriesRequest request) {
        return ResponseEntity.status(201).body(sessionSeriesService.createSeries(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read a recurring session series", description = "Returns the saved schedule and current generation horizon for editing a series.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Series schedule", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionSeriesResponse.class))),
            @ApiResponse(responseCode = "404", description = "Series not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<SessionSeriesResponse> getSeries(@PathVariable UUID id) {
        return ResponseEntity.ok(sessionSeriesService.getSeries(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Replace a recurring series schedule", description = "Updates the entire series and its future planned occurrences. ENTIRE_SERIES is currently the only supported applyTo mode.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Series schedule updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionSeriesResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid schedule or unsupported applyTo mode", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "404", description = "Series not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Inactive series cannot be edited", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<SessionSeriesResponse> updateSeries(
            @PathVariable UUID id,
            @RequestBody UpdateSessionSeriesRequest request
    ) {
        return ResponseEntity.ok(sessionSeriesService.updateSeries(id, request));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a recurring series", description = "Cancels future planned occurrences and leaves completed history unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Series cancelled"),
            @ApiResponse(responseCode = "404", description = "Series not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<Void> cancelSeries(@PathVariable UUID id) {
        sessionSeriesService.cancelSeries(id);
        return ResponseEntity.noContent().build();
    }
}