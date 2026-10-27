package com.openlinklearn.push.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class VapidWebPushProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsFcmErrorStatusAndCodeAndRedactsUrlsAndLongTokens() {
        String responseBody = """
                {
                  "error": {
                    "code": 403,
                    "status": "PERMISSION_DENIED",
                    "message": "Request to https://fcm.googleapis.com/fcm/send/private-token denied for bearer abcdefghijklmnopqrstuvwxyz0123456789",
                    "details": [
                      {"errorCode": "THIRD_PARTY_AUTH_ERROR"}
                    ]
                  }
                }
                """;

        String reason = VapidWebPushProvider.extractProviderReason(responseBody, objectMapper);

        assertEquals(
                "PERMISSION_DENIED; THIRD_PARTY_AUTH_ERROR; Request to [url] denied for [redacted]",
                reason);
        assertFalse(reason.contains("private-token"));
        assertFalse(reason.contains("abcdefghijklmnopqrstuvwxyz0123456789"));
    }

    @Test
    void ignoresNonJsonAndNonErrorProviderBodies() {
        assertNull(VapidWebPushProvider.extractProviderReason("forbidden", objectMapper));
        assertNull(VapidWebPushProvider.extractProviderReason("{\"message\":\"forbidden\"}", objectMapper));
    }
}
