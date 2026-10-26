package com.openlinklearn.dashboard.api;

import com.openlinklearn.dashboard.application.DashboardService;
import com.openlinklearn.dashboard.application.dto.DashboardResponse;
import com.openlinklearn.dashboard.application.dto.NextSessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void returnsTheDashboardContract() throws Exception {
        UUID sessionId = UUID.fromString("7b3b7a3e-5cf4-4f80-ae44-8c83c7a3f55e");
        when(dashboardService.getDashboard()).thenReturn(new DashboardResponse(
                12,
                34,
                2,
                1,
                new NextSessionResponse(sessionId, Instant.parse("2026-10-26T06:30:00Z"), "Example video title")));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingContentCount").value(12))
                .andExpect(jsonPath("$.completedContentCount").value(34))
                .andExpect(jsonPath("$.todaySessionCount").value(2))
                .andExpect(jsonPath("$.unassignedUpcomingSessionCount").value(1))
                .andExpect(jsonPath("$.nextSession.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.nextSession.startsAt").value("2026-10-26T06:30:00Z"))
                .andExpect(jsonPath("$.nextSession.title").value("Example video title"));

        verify(dashboardService).getDashboard();
    }
}