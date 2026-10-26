package com.openlinklearn;

import com.openlinklearn.content.api.HistoryController;
import com.openlinklearn.content.api.ContentController;
import com.openlinklearn.content.application.ContentService;
import com.openlinklearn.profile.api.SettingsController;
import com.openlinklearn.profile.application.SettingsService;
import com.openlinklearn.session.api.SessionController;
import com.openlinklearn.session.api.SessionSeriesController;
import com.openlinklearn.session.application.SessionSeriesService;
import com.openlinklearn.session.application.SessionService;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ContentController.class, HistoryController.class, SessionController.class, SettingsController.class, SessionSeriesController.class})
@Import({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
class OpenApiDocumentTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentService contentService;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private SettingsService settingsService;

    @MockitoBean
    private SessionSeriesService sessionSeriesService;

    @Test
    void publishesOnlyCurrentlyImplementedApiPathsAndDtos() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Open Link Learn API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.paths['/api/v1/content'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/content'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/content/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/content/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/content/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/content/{id}/complete'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sessions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sessions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/settings'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/settings'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/history'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/session-series'].post").exists())
                .andExpect(jsonPath("$.components.schemas.CreateContentRequest").exists())
                .andExpect(jsonPath("$.components.schemas.ContentResponse").exists())
                .andExpect(jsonPath("$.components.schemas.HistoryPageResponse").exists())
                .andExpect(jsonPath("$.components.schemas.HistoryItemResponse.properties.completedAt.format").value("date-time"))
                .andExpect(jsonPath("$.components.schemas.CreateSessionRequest").exists())
                .andExpect(jsonPath("$.components.schemas.SessionResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CreateSessionSeriesRequest").exists())
                .andExpect(jsonPath("$.components.schemas.SessionSeriesResponse.properties.generatedThrough.format").value("date"))
                .andExpect(jsonPath("$.components.schemas.SettingsResponse").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateSettingsRequest.properties.defaultReminderMinutesBefore.minimum").value(0))
                .andExpect(jsonPath("$.components.schemas.UpdateSettingsRequest.properties.defaultReminderMinutesBefore.maximum").value(1440))
                .andExpect(jsonPath("$.components.schemas.UpdateSettingsRequest.properties.defaultAssignmentCutoffMinutesBefore.maximum").value(10080))
                .andExpect(jsonPath("$.components.schemas.UpdateSettingsRequest.properties.recurrenceHorizonDays.minimum").value(7))
                .andExpect(jsonPath("$.components.schemas.UpdateSettingsRequest.properties.recurrenceHorizonDays.maximum").value(365))
                .andExpect(jsonPath("$.components.schemas.UpdateSettingsRequest.properties.applyToFutureSessions").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApiProblem.properties.status.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.CreateContentRequest.properties.url.description").exists())
                .andExpect(jsonPath("$.components.schemas.CreateContentRequest.properties.type.description").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateContentRequest.properties.estimatedDurationMinutes.minimum").value(1))
                .andExpect(jsonPath("$.components.schemas.UpdateContentRequest.properties.estimatedDurationMinutes.description").value("Positive estimated duration in minutes; null clears the estimate and omission leaves it unchanged."))
                .andExpect(jsonPath("$.components.schemas.CreateSessionRequest.properties.durationMinutes.maximum").value(720))
                .andExpect(jsonPath("$.paths['/api/v1/content'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/content'].post.responses['201'].content['application/json'].schema.$ref").value("#/components/schemas/ContentResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/content'].post.responses['400'].content['application/problem+json'].schema.$ref").value("#/components/schemas/ApiProblem"))
                .andExpect(jsonPath("$.paths['/api/v1/history'].get.responses['200'].content['application/json'].schema.$ref").value("#/components/schemas/HistoryPageResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/sessions'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sessions'].post.responses['201'].content['application/json'].schema.$ref").value("#/components/schemas/SessionResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/settings'].get.responses['200'].content['application/json'].schema.$ref").value("#/components/schemas/SettingsResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/content/{id}'].delete.responses['204']").exists());
    }
}