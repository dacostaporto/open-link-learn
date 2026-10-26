package com.openlinklearn.profile.api;

import com.openlinklearn.common.ApiProblem;
import com.openlinklearn.profile.application.SettingsService;
import com.openlinklearn.profile.application.dto.SettingsResponse;
import com.openlinklearn.profile.application.dto.UpdateSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings", description = "Owner profile defaults for new sessions and series")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
        @Operation(summary = "Get settings", description = "Returns the current local owner's profile defaults.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settings returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SettingsResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
        })
    public SettingsResponse getSettings() {
        return settingsService.getSettings();
    }

    @PatchMapping
    @Operation(
            summary = "Update profile defaults",
            description = "Replaces all four profile defaults. Existing sessions and series are not changed. Applying changed cutoffs to future sessions requires a separate operation that can reschedule pending jobs; job storage is not implemented yet."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settings updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SettingsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid timezone or setting bounds", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public SettingsResponse updateSettings(@RequestBody UpdateSettingsRequest request) {
        return settingsService.updateSettings(request);
    }
}