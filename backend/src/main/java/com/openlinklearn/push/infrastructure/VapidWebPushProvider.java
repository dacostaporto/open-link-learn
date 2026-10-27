package com.openlinklearn.push.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;

public class VapidWebPushProvider implements WebPushProvider {

    private static final int MAX_PROVIDER_BODY_BYTES = 16 * 1024;
    private static final int MAX_REASON_LENGTH = 300;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_VALUE = Pattern.compile("(?i)\\b(?:bearer\\s+)?[A-Za-z0-9_+/=-]{32,}\\b");

    private final PushService pushService;
    private final ObjectMapper objectMapper;

    public VapidWebPushProvider(VapidProperties properties, ObjectMapper objectMapper) {
        if (properties.vapidPublicKey() == null || properties.vapidPublicKey().isBlank()
                || properties.vapidPrivateKey() == null || properties.vapidPrivateKey().isBlank()
                || properties.vapidSubject() == null || properties.vapidSubject().isBlank()) {
            throw new IllegalStateException("Complete VAPID configuration is required when push delivery is enabled.");
        }
        this.objectMapper = objectMapper;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            pushService = new PushService(
                    properties.vapidPublicKey(),
                    properties.vapidPrivateKey(),
                    properties.vapidSubject());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("VAPID key configuration is invalid.");
        }
    }

    @Override
    public void send(Subscription subscription, String payload, int ttlSeconds) {
        try {
                Notification notification = Notification.builder()
                    .endpoint(subscription.endpoint())
                    .userPublicKey(subscription.p256dhKey())
                    .userAuth(subscription.authKey())
                    .payload(payload)
                    .ttl(ttlSeconds)
                    .build();
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                boolean expired = statusCode == 404 || statusCode == 410;
                boolean retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500;
                String providerReason;
                try {
                    providerReason = extractProviderReason(readErrorBody(response), objectMapper);
                } catch (IOException exception) {
                    providerReason = "provider_error_body_unavailable";
                }
                throw new WebPushProviderException(
                        statusCode, expired, retryable, providerReason);
            }
        } catch (WebPushProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WebPushProviderException(true);
        } catch (IOException | ExecutionException exception) {
            throw new WebPushProviderException(true);
        } catch (GeneralSecurityException | JoseException exception) {
            throw new WebPushProviderException(false);
        }
    }

    static String extractProviderReason(String responseBody, ObjectMapper objectMapper) {
        if (responseBody == null || responseBody.isBlank()) {
            return "provider_response_body_empty";
        }
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            if (error.isObject()) {
                List<String> reasons = new ArrayList<>();
                addReason(reasons, error.path("status").asText(null));
                JsonNode details = error.path("details");
                if (details.isArray()) {
                    for (JsonNode detail : details) {
                        addReason(reasons, detail.path("errorCode").asText(null));
                    }
                }
                reasons.add(sanitizeProviderMessage(error.path("message").asText(null)));
                String reason = reasons.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .reduce((first, second) -> first + "; " + second)
                        .orElse(null);
                if (reason != null) {
                    return reason.substring(0, Math.min(reason.length(), MAX_REASON_LENGTH));
                }
            }
        } catch (IOException exception) {
            return sanitizeProviderMessage(responseBody);
        }
        return sanitizeProviderMessage(responseBody);
    }

    private static void addReason(List<String> reasons, String value) {
        if (value != null && value.matches("[A-Z0-9_]{1,80}")) {
            reasons.add(value);
        }
    }

    private static String sanitizeProviderMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String sanitized = CONTROL_CHARACTERS.matcher(message).replaceAll(" ");
        sanitized = URL.matcher(sanitized).replaceAll("[url]");
        sanitized = SENSITIVE_VALUE.matcher(sanitized).replaceAll("[redacted]");
        return sanitized.substring(0, Math.min(sanitized.length(), MAX_REASON_LENGTH));
    }

    private static String readErrorBody(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return null;
        }
        byte[] body;
        try {
            body = entity.getContent().readNBytes(MAX_PROVIDER_BODY_BYTES + 1);
        } finally {
            EntityUtils.consume(entity);
        }
        if (body.length > MAX_PROVIDER_BODY_BYTES) {
            return "provider_response_body_too_large";
        }
        return new String(body, StandardCharsets.UTF_8);
    }
}