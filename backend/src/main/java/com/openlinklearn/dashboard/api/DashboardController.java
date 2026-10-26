package com.openlinklearn.dashboard.api;

import com.openlinklearn.common.ApiProblem;
import com.openlinklearn.dashboard.application.DashboardService;
import com.openlinklearn.dashboard.application.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Owner-scoped learning and session summary")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Get dashboard summary", description = "Returns owner-scoped content and session counts using the configured timezone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard summary returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DashboardResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public DashboardResponse getDashboard() {
        return dashboardService.getDashboard();
    }
}