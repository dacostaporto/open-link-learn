package com.openlinklearn.common;

import com.openlinklearn.content.api.ContentController;
import com.openlinklearn.content.application.ContentService;
import com.openlinklearn.content.application.dto.CreateContentRequest;
import com.openlinklearn.content.application.dto.UpdateContentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentController.class)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentService contentService;

    @Test
    void mapsValidationFailureToProblemDetails() throws Exception {
        when(contentService.createContent(new CreateContentRequest("", null)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "A content URL is required."));

        mockMvc.perform(post("/api/v1/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\",\"title\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("A content URL is required."))
                .andExpect(jsonPath("$.instance").value("/api/v1/content"));
    }

    @Test
    void mapsMalformedRequestBodyToProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed."))
                .andExpect(jsonPath("$.instance").value("/api/v1/content"));
    }

    @Test
    void mapsNotFoundToProblemDetails() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
        when(contentService.getContent(id)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found.")
        );

        mockMvc.perform(get("/api/v1/content/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Content was not found."))
                .andExpect(jsonPath("$.instance").value("/api/v1/content/" + id));
    }

    @Test
    void mapsConflictToProblemDetails() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
        when(contentService.updateContent(id, new UpdateContentRequest("Updated title")))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Content is already assigned."));

        mockMvc.perform(patch("/api/v1/content/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Content is already assigned."));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
        when(contentService.getContent(id)).thenThrow(new IllegalStateException("secret database credentials"));

        mockMvc.perform(get("/api/v1/content/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret database credentials")
                )));
    }
}