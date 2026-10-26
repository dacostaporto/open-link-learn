package com.openlinklearn.dashboard.application.dto;

public record DashboardResponse(
        long pendingContentCount,
        long completedContentCount,
        long todaySessionCount,
        long unassignedUpcomingSessionCount,
        NextSessionResponse nextSession
) {
}