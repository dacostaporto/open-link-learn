package com.openlinklearn.push.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openlinklearn.push.application.PushSubscriptionService;
import com.openlinklearn.push.domain.PushSubscription;
import com.openlinklearn.push.domain.PushSubscriptionRepository;
import com.openlinklearn.session.domain.ReminderDeliveryException;
import com.openlinklearn.session.domain.ReminderSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class VapidReminderSenderTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("7b3b7a3e-5cf4-4f80-ae44-8c83c7a3f55e");
    private static final Instant NOW = Instant.parse("2026-10-26T06:20:00Z");
    private static final String ENDPOINT = "https://push.example/private-endpoint-token";
    private static final String P256DH = "p256dh-test-material";
    private static final String AUTH = "auth-test-material";

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private PushSubscriptionService subscriptionService;

    private FakeWebPushProvider provider;
    private VapidReminderSender sender;
    private PushSubscription subscription;

    @BeforeEach
    void setUp() {
        provider = new FakeWebPushProvider();
        sender = new VapidReminderSender(
                provider,
                subscriptionRepository,
                subscriptionService,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        subscription = new PushSubscription(
                UUID.randomUUID(), OWNER_ID, ENDPOINT, P256DH, AUTH, "Fake browser", NOW, NOW, true);
        when(subscriptionRepository.findByOwnerIdAndActiveTrueOrderByCreatedAtAsc(OWNER_ID))
                .thenReturn(List.of(subscription));
    }

    @Test
    void sendsMinimalReminderPayloadWithSessionExpiryTtl() throws Exception {
        sender.send(delivery(10, NOW.plusSeconds(600)));

        assertEquals(1, provider.sent.size());
        WebPushProvider.Subscription recipient = provider.sent.get(0).subscription();
        assertEquals(ENDPOINT, recipient.endpoint());
        assertEquals(600, provider.sent.get(0).ttlSeconds());
        JsonNode payload = new ObjectMapper().readTree(provider.sent.get(0).payload());
        assertEquals(4, payload.size());
        assertEquals("Open Link Learn", payload.get("title").asText());
        assertEquals("Your planned session starts in 10 minutes.", payload.get("body").asText());
        assertEquals("/calendar?sessionId=" + SESSION_ID, payload.get("url").asText());
        assertEquals(SESSION_ID.toString(), payload.get("sessionId").asText());
    }

    @Test
    void retriesTransientProviderFailure() {
        provider.failure = new WebPushProviderException(503, false, true);

        ReminderDeliveryException exception = assertThrows(
                ReminderDeliveryException.class,
                () -> sender.send(delivery(10, NOW.plusSeconds(600))));

        assertEquals(true, exception.isRetryable());
        verify(subscriptionService, never()).deactivateExpired(subscription.getId());
    }

    @Test
    void logsSafeSubscriptionIdAndProviderStatusForPermanentFailure(CapturedOutput output) {
        provider.failure = new WebPushProviderException(
                403, false, false, "PERMISSION_DENIED; THIRD_PARTY_AUTH_ERROR");

        assertThrows(ReminderDeliveryException.class, () -> sender.send(delivery(10, NOW.plusSeconds(600))));

        assertTrue(output.getOut().contains("push_subscription_delivery_failure"));
        assertTrue(output.getOut().contains(subscription.getId().toString()));
        assertTrue(output.getOut().matches("(?s).*provider_status_code.*403.*"));
        assertTrue(output.getOut().contains("THIRD_PARTY_AUTH_ERROR"));
        assertFalse(output.getOut().contains(ENDPOINT));
        assertFalse(output.getOut().contains(P256DH));
        assertFalse(output.getOut().contains(AUTH));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void deactivatesDefinitivelyExpiredSubscriptionWithoutRetry(int statusCode) {
        provider.failure = new WebPushProviderException(statusCode, true, false);

        sender.send(delivery(10, NOW.plusSeconds(600)));

        verify(subscriptionService).deactivateExpired(subscription.getId());
    }

    @Test
    void payloadAndProviderRepresentationsNeverContainEndpointsOrKeys() throws Exception {
        sender.send(delivery(1, NOW.plusSeconds(60)));

        String payload = provider.sent.get(0).payload();
        assertFalse(payload.contains(ENDPOINT));
        assertFalse(payload.contains(P256DH));
        assertFalse(payload.contains(AUTH));
        assertFalse(provider.sent.get(0).subscription().toString().contains(ENDPOINT));
        assertFalse(provider.sent.get(0).subscription().toString().contains(P256DH));
        assertFalse(provider.sent.get(0).subscription().toString().contains(AUTH));
        assertFalse(new WebPushProviderException(410, true, false).toString().contains(ENDPOINT));
    }

    @Test
    void operationalLogsContainDeliverySummaryWithoutSubscriptionValues(CapturedOutput output) {
        sender.send(delivery(1, NOW.plusSeconds(60)));

        assertTrue(output.getOut().contains("push_reminder_delivery"));
        assertFalse(output.getOut().contains(ENDPOINT));
        assertFalse(output.getOut().contains(P256DH));
        assertFalse(output.getOut().contains(AUTH));
    }

    private static ReminderSender.ReminderDelivery delivery(int minutesBefore, Instant startsAt) {
        return new ReminderSender.ReminderDelivery(OWNER_ID, SESSION_ID, startsAt, minutesBefore);
    }

    private static final class FakeWebPushProvider implements WebPushProvider {
        private final java.util.ArrayList<SentPush> sent = new java.util.ArrayList<>();
        private WebPushProviderException failure;

        @Override
        public void send(Subscription subscription, String payload, int ttlSeconds) {
            if (failure != null) {
                throw failure;
            }
            sent.add(new SentPush(subscription, payload, ttlSeconds));
        }
    }

    private record SentPush(WebPushProvider.Subscription subscription, String payload, int ttlSeconds) {
    }
}