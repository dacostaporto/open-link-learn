package com.openlinklearn.profile.api;

import com.openlinklearn.profile.application.SettingsService;
import com.openlinklearn.profile.application.dto.SettingsResponse;
import com.openlinklearn.profile.application.dto.UpdateSettingsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettingsService settingsService;

    @Test
    void returnsConfiguredSettings() throws Exception {
        when(settingsService.getSettings()).thenReturn(new SettingsResponse("Etc/UTC", 10, 30, 90));

        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Etc/UTC"))
                .andExpect(jsonPath("$.defaultReminderMinutesBefore").value(10))
                .andExpect(jsonPath("$.defaultAssignmentCutoffMinutesBefore").value(30))
                .andExpect(jsonPath("$.recurrenceHorizonDays").value(90));
    }

    @Test
    void patchesAllProfileDefaults() throws Exception {
        SettingsResponse updated = new SettingsResponse("Europe/Paris", 20, 45, 120);
        when(settingsService.updateSettings(any(UpdateSettingsRequest.class))).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/settings")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"timezone":"Europe/Paris","defaultReminderMinutesBefore":20,
                                 "defaultAssignmentCutoffMinutesBefore":45,"recurrenceHorizonDays":120}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Paris"))
                .andExpect(jsonPath("$.defaultReminderMinutesBefore").value(20))
                .andExpect(jsonPath("$.defaultAssignmentCutoffMinutesBefore").value(45))
                .andExpect(jsonPath("$.recurrenceHorizonDays").value(120));

        verify(settingsService).updateSettings(new UpdateSettingsRequest("Europe/Paris", 20, 45, 120));
    }

    @Test
    void rejectsFutureSessionApplicationUntilJobAwareOperationExists() throws Exception {
        mockMvc.perform(patch("/api/v1/settings")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"timezone":"Europe/Paris","defaultReminderMinutesBefore":20,
                                 "defaultAssignmentCutoffMinutesBefore":45,"recurrenceHorizonDays":120,
                                 "applyToFutureSessions":true}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(settingsService);
    }
}