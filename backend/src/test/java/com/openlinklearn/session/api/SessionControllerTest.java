package com.openlinklearn.session.api;

import com.openlinklearn.session.application.SessionService;
import com.openlinklearn.session.application.dto.CreateSessionRequest;
import com.openlinklearn.session.application.dto.SessionListResponse;
import com.openlinklearn.session.application.dto.SessionResponse;
import com.openlinklearn.session.application.dto.UpdateSessionRequest;
import com.openlinklearn.session.domain.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @Test
    void createsOneOffSession() throws Exception {
        UUID id = UUID.fromString("a51a8fbf-20dd-4bce-bcc8-44a4ed4ea290");
        when(sessionService.createSession(new CreateSessionRequest(
                Instant.parse("2026-10-26T06:30:00Z"), 30, "Europe/Madrid", null, 10, 30
        ))).thenReturn(new SessionResponse(
                id, null, Instant.parse("2026-10-26T06:30:00Z"), Instant.parse("2026-10-26T07:00:00Z"),
                "Europe/Madrid", null, SessionStatus.PLANNED, 10, 30, null
        ));

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"2026-10-26T06:30:00Z","durationMinutes":30,"timezone":"Europe/Madrid","reminderMinutesBefore":10,"assignmentCutoffMinutesBefore":30}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sessions/" + id))
                .andExpect(jsonPath("$.endsAt").value("2026-10-26T07:00:00Z"))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void listsSessionsInRequestedInstantRange() throws Exception {
        Instant startsAt = Instant.parse("2026-10-26T06:30:00Z");
        when(sessionService.getSessions(Instant.parse("2026-10-26T00:00:00Z"), Instant.parse("2026-10-27T00:00:00Z")))
                .thenReturn(new SessionListResponse(List.of(new SessionResponse(
                        UUID.fromString("a51a8fbf-20dd-4bce-bcc8-44a4ed4ea290"), null,
                        startsAt, startsAt.plusSeconds(1800), "Europe/Madrid", null,
                        SessionStatus.PLANNED, 10, 30, null
                ))));

        mockMvc.perform(get("/api/v1/sessions")
                        .queryParam("from", "2026-10-26T00:00:00Z")
                        .queryParam("to", "2026-10-27T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.items[0].startsAt").value("2026-10-26T06:30:00Z"));
    }

    @Test
    void getsOneSessionById() throws Exception {
        UUID id = UUID.fromString("a51a8fbf-20dd-4bce-bcc8-44a4ed4ea290");
        when(sessionService.getSessionById(id)).thenReturn(new SessionResponse(
                id, null, Instant.parse("2026-10-26T06:30:00Z"), Instant.parse("2026-10-26T07:00:00Z"),
                "Europe/Madrid", null, SessionStatus.PLANNED, 5, 30, null
        ));

        mockMvc.perform(get("/api/v1/sessions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.timezone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.reminderMinutesBefore").value(5));
    }

    @Test
    void updatesSessionThroughPatchEndpoint() throws Exception {
        UUID id = UUID.fromString("a51a8fbf-20dd-4bce-bcc8-44a4ed4ea290");
        when(sessionService.updateSession(eq(id), any(UpdateSessionRequest.class))).thenReturn(new SessionResponse(
                id, null, Instant.parse("2026-10-26T07:00:00Z"), Instant.parse("2026-10-26T07:25:00Z"),
                "Europe/Madrid", null, SessionStatus.PLANNED, 5, 45, null
        ));

        mockMvc.perform(patch("/api/v1/sessions/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"2026-10-26T07:00:00Z","durationMinutes":25,"reminderMinutesBefore":5,"assignmentCutoffMinutesBefore":45}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value("2026-10-26T07:00:00Z"))
                .andExpect(jsonPath("$.endsAt").value("2026-10-26T07:25:00Z"))
                .andExpect(jsonPath("$.reminderMinutesBefore").value(5))
                .andExpect(jsonPath("$.assignmentCutoffMinutesBefore").value(45));
    }

    @Test
    void mapsPatchValidationNotFoundAndConflictErrors() throws Exception {
        UUID id = UUID.fromString("a51a8fbf-20dd-4bce-bcc8-44a4ed4ea290");
        when(sessionService.updateSession(eq(id), any(UpdateSessionRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid update."));
        mockMvc.perform(patch("/api/v1/sessions/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":721}"))
                .andExpect(status().isBadRequest());

        when(sessionService.updateSession(eq(id), any(UpdateSessionRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session was not found."));
        mockMvc.perform(patch("/api/v1/sessions/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":25}"))
                .andExpect(status().isNotFound());

        when(sessionService.updateSession(eq(id), any(UpdateSessionRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Content is already assigned."));
        mockMvc.perform(patch("/api/v1/sessions/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentItemId\":\"3e16a2f1-cf35-46f1-9d75-7ac12d31b81f\"}"))
                .andExpect(status().isConflict());
    }

        @Test
        void completesSessionThroughActionEndpoint() throws Exception {
                UUID id = UUID.fromString("a51a8fbf-20dd-4bce-bcc8-44a4ed4ea290");
                when(sessionService.completeSession(id)).thenReturn(new SessionResponse(
                                id, null, Instant.parse("2026-10-26T07:00:00Z"), Instant.parse("2026-10-26T07:25:00Z"),
                                "Europe/Madrid", null, SessionStatus.COMPLETED, 5, 45,
                                Instant.parse("2026-10-26T07:25:00Z")
                ));

                mockMvc.perform(post("/api/v1/sessions/{id}/complete", id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.completedAt").value("2026-10-26T07:25:00Z"));
        }

        @Test
        void skipsAndCancelsSessionsThroughActionEndpoints() throws Exception {
                UUID id = UUID.fromString("a51a8fbf-20dd-4bce-bcc8-44a4ed4ea290");
                when(sessionService.skipSession(id)).thenReturn(new SessionResponse(
                                id, null, Instant.parse("2026-10-26T07:00:00Z"), Instant.parse("2026-10-26T07:25:00Z"),
                                "Europe/Madrid", null, SessionStatus.SKIPPED, 5, 45, null
                ));
                when(sessionService.cancelSession(id)).thenReturn(new SessionResponse(
                                id, null, Instant.parse("2026-10-26T07:00:00Z"), Instant.parse("2026-10-26T07:25:00Z"),
                                "Europe/Madrid", null, SessionStatus.CANCELLED, 5, 45, null
                ));

                mockMvc.perform(post("/api/v1/sessions/{id}/skip", id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("SKIPPED"))
                                .andExpect(jsonPath("$.content").doesNotExist());
                mockMvc.perform(post("/api/v1/sessions/{id}/cancel", id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CANCELLED"))
                                .andExpect(jsonPath("$.content").doesNotExist());
        }
}