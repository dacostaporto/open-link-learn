package com.openlinklearn.content.api;

import com.openlinklearn.content.application.ContentService;
import com.openlinklearn.content.application.dto.HistoryItemResponse;
import com.openlinklearn.content.application.dto.HistoryPageResponse;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.content.domain.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
class HistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentService contentService;

    @Test
    void returnsTheDocumentedHistoryPageAndForwardsSearchAndPagination() throws Exception {
        UUID id = UUID.fromString("3e16a2f1-cf35-46f1-9d75-7ac12d31b81f");
        Instant completedAt = Instant.parse("2026-10-22T14:25:00Z");
        when(contentService.getHistory("English", 1, 5)).thenReturn(new HistoryPageResponse(
                List.of(new HistoryItemResponse(
                        id,
                        "https://www.youtube.com/watch?v=lesson",
                        "English lesson",
                        ContentType.YOUTUBE,
                        ContentStatus.COMPLETED,
                        completedAt
                )),
                1,
                5,
                6,
                2
        ));

        mockMvc.perform(get("/api/v1/history")
                        .queryParam("search", "English")
                        .queryParam("page", "1")
                        .queryParam("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].completedAt").value("2026-10-22T14:25:00Z"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalItems").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(contentService).getHistory("English", 1, 5);
    }
}