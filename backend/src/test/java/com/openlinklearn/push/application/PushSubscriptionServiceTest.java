package com.openlinklearn.push.application;

import com.openlinklearn.push.application.dto.PushSubscriptionRequest;
import com.openlinklearn.push.application.dto.PushSubscriptionResponse;
import com.openlinklearn.push.domain.PushSubscription;
import com.openlinklearn.push.domain.PushSubscriptionRepository;
import com.openlinklearn.push.infrastructure.VapidProperties;
import com.openlinklearn.push.infrastructure.WebPushProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private PushSubscriptionRepository repository;

    private PushSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new PushSubscriptionService(repository);
    }

    @Test
    void refreshesExistingEndpointAndReturnsOnlySafeFields() {
        UUID existingId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-22T14:00:00Z");
        String endpoint = "https://push.example/subscription/private-token";
        PushSubscription refreshed = new PushSubscription(
                existingId, OWNER_ID, endpoint, validP256dh(), validAuth(), "Updated browser",
                createdAt, Instant.parse("2026-10-23T14:00:00Z"), true);
        when(repository.upsertByEndpoint(any(), eq(OWNER_ID), eq(endpoint), eq(validP256dh()), eq(validAuth()),
                eq("Updated browser"), any(Instant.class))).thenReturn(1);
        when(repository.findByOwnerIdAndEndpoint(OWNER_ID, endpoint)).thenReturn(Optional.of(refreshed));

        PushSubscriptionResponse response = service.subscribe(new PushSubscriptionRequest(
                endpoint, new PushSubscriptionRequest.Keys(validP256dh(), validAuth()), "Updated browser"));

        assertEquals(existingId, response.id());
        assertEquals(createdAt, response.createdAt());
        assertEquals(true, response.active());
        verify(repository).upsertByEndpoint(any(), eq(OWNER_ID), eq(endpoint), eq(validP256dh()), eq(validAuth()),
                eq("Updated browser"), any(Instant.class));
    }

    @Test
    void rejectsNonHttpsEndpointWithoutCallingRepository() {
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                "http://push.example/subscription", new PushSubscriptionRequest.Keys(validP256dh(), validAuth()), null);

        assertThrows(ResponseStatusException.class, () -> service.subscribe(request));

        verify(repository, never()).upsertByEndpoint(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsMalformedKeyMaterialWithoutCallingRepository() {
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                "https://push.example/subscription", new PushSubscriptionRequest.Keys("not-a-key", validAuth()), null);

        assertThrows(ResponseStatusException.class, () -> service.subscribe(request));

        verify(repository, never()).upsertByEndpoint(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void requestStringRepresentationRedactsEndpointAndKeyMaterial() {
        String endpoint = "https://push.example/full-endpoint-token";
        String p256dh = validP256dh();
        String auth = validAuth();
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                endpoint, new PushSubscriptionRequest.Keys(p256dh, auth), null);

        assertFalse(request.toString().contains(endpoint));
        assertFalse(request.toString().contains(p256dh));
        assertFalse(request.toString().contains(auth));
        assertFalse(request.keys().toString().contains(p256dh));
        assertFalse(request.keys().toString().contains(auth));
        WebPushProvider.Subscription subscription = new WebPushProvider.Subscription(endpoint, p256dh, auth);
        assertFalse(subscription.toString().contains(endpoint));
        assertFalse(subscription.toString().contains(p256dh));
        assertFalse(subscription.toString().contains(auth));
        String vapidProperties = new VapidProperties(true, "public-key-secret", "private-key-secret",
                "mailto:private@example.invalid").toString();
        assertFalse(vapidProperties.contains("public-key-secret"));
        assertFalse(vapidProperties.contains("private-key-secret"));
        assertFalse(vapidProperties.contains("private@example.invalid"));
    }

    @Test
    void endpointOwnedByAnotherProfileDoesNotGetTransferred() {
        String endpoint = "https://push.example/subscription/private-token";
        when(repository.upsertByEndpoint(any(), eq(OWNER_ID), eq(endpoint), eq(validP256dh()), eq(validAuth()),
                any(), any(Instant.class))).thenReturn(0);

        assertThrows(ResponseStatusException.class, () -> service.subscribe(new PushSubscriptionRequest(
                endpoint, new PushSubscriptionRequest.Keys(validP256dh(), validAuth()), null)));

        verify(repository, never()).findByOwnerIdAndEndpoint(any(), any());
    }

    private static String validP256dh() {
        byte[] bytes = new byte[65];
        bytes[0] = 4;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String validAuth() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    }
}