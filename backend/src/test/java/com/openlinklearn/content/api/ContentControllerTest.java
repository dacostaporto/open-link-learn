package com.openlinklearn.content.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.openlinklearn.config.ApiCorsConfiguration;
import com.openlinklearn.content.application.ContentService;
import com.openlinklearn.content.application.dto.ContentPageResponse;
import com.openlinklearn.content.application.dto.ContentResponse;
import com.openlinklearn.content.application.dto.CreateContentRequest;
import com.openlinklearn.content.application.dto.UpdateContentRequest;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.content.domain.DuplicateContentException;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ContentController.class, properties = "openlinklearn.cors.allowed-origin=http://localhost:4200,http://localhost:18081")
@Import(ApiCorsConfiguration.class)
class ContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentService contentService;

        @Test
        void allowsConfiguredDevelopmentOriginWithoutCredentials() throws Exception {
                mockMvc.perform(options("/api/v1/content")
                                                .header("Origin", "http://localhost:4200")
                                                .header("Access-Control-Request-Method", "GET"))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }

        @Test
        void allowsConfiguredComposeOriginWithoutCredentials() throws Exception {
                mockMvc.perform(options("/api/v1/content")
                                                .header("Origin", "http://localhost:18081")
                                                .header("Access-Control-Request-Method", "POST"))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:18081"))
                                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }

        @Test
        void rejectsUnconfiguredCorsOrigins() throws Exception {
                mockMvc.perform(options("/api/v1/content")
                                                .header("Origin", "https://untrusted.example")
                                                .header("Access-Control-Request-Method", "GET"))
                                .andExpect(status().isForbidden())
                                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }

    @Test
    void listsContentWithFiltersAndPagination() throws Exception {
        ContentResponse item = new ContentResponse(
                UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f"),
                "https://example.org/learn",
                "https://example.org/learn",
                "English lesson",
                ContentType.ARTICLE,
                null,
                "example.org",
                null,
                ContentStatus.PENDING,
                null,
                Instant.parse("2026-10-23T10:00:00Z"),
                null
        );
        when(contentService.getContent(ContentStatus.PENDING, ContentType.ARTICLE, "English", 0, 20))
                .thenReturn(new ContentPageResponse(java.util.List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/content")
                        .queryParam("status", "PENDING")
                        .queryParam("type", "ARTICLE")
                        .queryParam("search", "English")
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("English lesson"))
                .andExpect(jsonPath("$.items[0].type").value("ARTICLE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getsContentById() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
        when(contentService.getContent(id)).thenReturn(new ContentResponse(
                id,
                "https://example.org/learn",
                "https://example.org/learn",
                "English lesson",
                ContentType.ARTICLE,
                null,
                "example.org",
                null,
                ContentStatus.PENDING,
                null,
                Instant.parse("2026-10-23T10:00:00Z"),
                null
        ));

        mockMvc.perform(get("/api/v1/content/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("English lesson"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void deletesContentAndReturnsNoContent() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");

        mockMvc.perform(delete("/api/v1/content/{id}", id))
                .andExpect(status().isNoContent());

        verify(contentService).deleteContent(id);
    }

    @Test
    void createsContentAndReturnsTheDocumentedResponseShape() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
        when(contentService.createContent(new CreateContentRequest("https://example.org/learn", null)))
                .thenReturn(new ContentResponse(
                        id,
                        "https://example.org/learn",
                        "https://example.org/learn",
                        "example.org",
                        ContentType.ARTICLE,
                        null,
                        "example.org",
                        null,
                        ContentStatus.PENDING,
                        null,
                        Instant.parse("2026-10-23T10:00:00Z"),
                        null
                ));

        mockMvc.perform(post("/api/v1/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.org/learn","title":null}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/content/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value("ARTICLE"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.assignedSessionId").doesNotExist());
    }

    @Test
    void updatesContentTitle() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
        when(contentService.updateContent(id, new UpdateContentRequest("Updated title")))
                .thenReturn(new ContentResponse(
                        id,
                        "https://example.org/learn",
                        "https://example.org/learn",
                        "Updated title",
                        ContentType.ARTICLE,
                        null,
                        "example.org",
                        null,
                        ContentStatus.PENDING,
                        null,
                        Instant.parse("2026-10-23T10:00:00Z"),
                        null
                ));

        mockMvc.perform(patch("/api/v1/content/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void updatesEstimatedDuration() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
        UpdateContentRequest request = new UpdateContentRequest();
        request.setEstimatedDurationMinutes(18);
        when(contentService.updateContent(id, request)).thenReturn(new ContentResponse(
                id,
                "https://example.org/learn",
                "https://example.org/learn",
                "English lesson",
                ContentType.ARTICLE,
                null,
                "example.org",
                18,
                ContentStatus.PENDING,
                null,
                Instant.parse("2026-10-23T10:00:00Z"),
                null
        ));

        mockMvc.perform(patch("/api/v1/content/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estimatedDurationMinutes\":18}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedDurationMinutes").value(18));
    }

    @Test
    void rejectsUnknownAndImmutableUpdateFields() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");

        mockMvc.perform(patch("/api/v1/content/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"url\":\"https://example.org/other\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returnsUsefulConflictForDuplicateUrls() throws Exception {
        when(contentService.createContent(new CreateContentRequest("https://example.org/learn", null)))
                .thenThrow(new DuplicateContentException());

        mockMvc.perform(post("/api/v1/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.org/learn\",\"title\":null}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("This URL is already in your backlog."));
    }

        @Test
        void completesContent() throws Exception {
                UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
                when(contentService.completeContent(id)).thenReturn(new ContentResponse(
                                id,
                                "https://example.org/learn",
                                "https://example.org/learn",
                                "Completed title",
                                ContentType.ARTICLE,
                                null,
                                "example.org",
                                null,
                                ContentStatus.COMPLETED,
                                null,
                                Instant.parse("2026-10-23T10:00:00Z"),
                                Instant.parse("2026-10-23T11:00:00Z")
                ));

                mockMvc.perform(post("/api/v1/content/{id}/complete", id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.completedAt").value("2026-10-23T11:00:00Z"));
        }

        @Test
        void reportsConflictWhenCompletingAssignedContentDirectly() throws Exception {
                UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
                when(contentService.completeContent(id)).thenThrow(new ResponseStatusException(
                                HttpStatus.CONFLICT, "Content assigned to an active session cannot be completed directly."));

                mockMvc.perform(post("/api/v1/content/{id}/complete", id))
                                .andExpect(status().isConflict());
        }
}