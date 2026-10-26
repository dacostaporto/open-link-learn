package com.openlinklearn.push.api;

import com.openlinklearn.push.application.PushSubscriptionService;
import com.openlinklearn.push.application.dto.PushSubscriptionResponse;
import com.openlinklearn.push.infrastructure.VapidProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PushSubscriptionController.class)
class PushSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushSubscriptionService pushSubscriptionService;

    @MockitoBean
    private VapidProperties vapidProperties;

    @Test
    void exposesOnlyConfiguredVapidPublicKey() throws Exception {
        when(vapidProperties.vapidPublicKey()).thenReturn("public-key-value");

        mockMvc.perform(get("/api/v1/push/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("public-key-value"))
                .andExpect(jsonPath("$.vapidPrivateKey").doesNotExist());
    }

    @Test
    void reportsUnavailableWhenVapidPublicKeyIsNotConfigured() throws Exception {
        when(vapidProperties.vapidPublicKey()).thenReturn("");

        mockMvc.perform(get("/api/v1/push/vapid-public-key"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void returnsSafeCreatedSubscriptionDto() throws Exception {
        UUID id = UUID.randomUUID();
        when(pushSubscriptionService.subscribe(any())).thenReturn(
                new PushSubscriptionResponse(id, true, Instant.parse("2026-10-22T14:00:00Z")));

        mockMvc.perform(post("/api/v1/push-subscriptions")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"endpoint":"https://push.example/private-token",
                                 "keys":{"p256dh":"public-material","auth":"secret-material"},
                                 "userAgent":"Test browser"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/push-subscriptions/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-10-22T14:00:00Z"))
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.keys").doesNotExist());
    }

    @Test
    void returnsNoContentWhenUnsubscribing() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/push-subscriptions/" + id))
                .andExpect(status().isNoContent());

        verify(pushSubscriptionService).unsubscribe(id);
    }
}