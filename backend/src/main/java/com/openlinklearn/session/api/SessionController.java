package com.openlinklearn.session.api;

import com.openlinklearn.common.ApiProblem;
import com.openlinklearn.session.application.SessionService;
import com.openlinklearn.session.application.dto.CreateSessionRequest;
import com.openlinklearn.session.application.dto.SessionListResponse;
import com.openlinklearn.session.application.dto.SessionResponse;
import com.openlinklearn.session.application.dto.UpdateSessionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "One-off study session scheduling")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
        @Operation(summary = "Create a one-off session", description = "Creates a planned session and optionally reserves eligible pending content.")
        @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid time, duration, timezone, reminder, or cutoff", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "404", description = "Requested content was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Requested content is not eligible or is already assigned", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
        })
    public ResponseEntity<SessionResponse> createSession(@RequestBody CreateSessionRequest request) {
        SessionResponse response = sessionService.createSession(request);
        return ResponseEntity.created(URI.create("/api/v1/sessions/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a session", description = "Returns one session and its assigned content summary in the local owner context.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public SessionResponse getSessionById(@PathVariable java.util.UUID id) {
        return sessionService.getSessionById(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a planned session", description = "Updates persisted session timing, content assignment, and reminder/cutoff overrides. Job scheduling is integrated in a later task.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or empty update", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "404", description = "Session or requested content was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Session is not editable or content is not eligible", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public SessionResponse updateSession(@PathVariable java.util.UUID id, @RequestBody UpdateSessionRequest request) {
        return sessionService.updateSession(id, request);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a session", description = "Completes the session and its assigned content atomically. Repeated completion returns the existing completed state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session completed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Session cannot be completed from its current state", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public SessionResponse completeSession(@PathVariable java.util.UUID id) {
        return sessionService.completeSession(id);
    }

    @PostMapping("/{id}/skip")
    @Operation(summary = "Skip a session", description = "Marks the session skipped and releases its assigned content, which remains pending. Repeating the action returns the same state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session skipped", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Session cannot be skipped from its current state", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public SessionResponse skipSession(@PathVariable java.util.UUID id) {
        return sessionService.skipSession(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a planned session", description = "Marks a planned session cancelled and releases its assigned content, which remains pending. Repeating the action returns the same state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session cancelled", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session was not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Session cannot be cancelled from its current state", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public SessionResponse cancelSession(@PathVariable java.util.UUID id) {
        return sessionService.cancelSession(id);
    }

    @GetMapping
        @Operation(summary = "List sessions in a time range", description = "Returns sessions overlapping the requested half-open time range.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session list returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or missing time range", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
        })
        public SessionListResponse getSessions(
            @Parameter(description = "Inclusive range start as an ISO-8601 instant", required = true, example = "2026-10-26T00:00:00Z") @RequestParam Instant from,
            @Parameter(description = "Exclusive range end as an ISO-8601 instant; must be after from", required = true, example = "2026-11-02T00:00:00Z") @RequestParam Instant to
        ) {
        return sessionService.getSessions(from, to);
    }
}