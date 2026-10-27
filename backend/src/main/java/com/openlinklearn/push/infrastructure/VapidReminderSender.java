package com.openlinklearn.push.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openlinklearn.push.application.PushSubscriptionService;
import com.openlinklearn.push.domain.PushSubscription;
import com.openlinklearn.push.domain.PushSubscriptionRepository;
import com.openlinklearn.session.domain.ReminderDeliveryException;
import com.openlinklearn.session.domain.ReminderSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class VapidReminderSender implements ReminderSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(VapidReminderSender.class);
    private static final int MAX_TTL_SECONDS = 24 * 60 * 60;
    private static final String TITLE = "Open Link Learn";

    private final WebPushProvider provider;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushSubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public VapidReminderSender(
            WebPushProvider provider,
            PushSubscriptionRepository subscriptionRepository,
            PushSubscriptionService subscriptionService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.provider = provider;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void send(ReminderDelivery delivery) {
        List<PushSubscription> subscriptions = subscriptionRepository
                .findByOwnerIdAndActiveTrueOrderByCreatedAtAsc(delivery.ownerId());
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload = createPayload(delivery);
        int ttlSeconds = ttlUntilSessionStart(delivery);
        int deliveredCount = 0;
        int expiredCount = 0;
        int retryableFailureCount = 0;
        int permanentFailureCount = 0;

        for (PushSubscription subscription : subscriptions) {
            WebPushProvider.Subscription recipient = new WebPushProvider.Subscription(
                    subscription.getEndpoint(),
                    subscription.getP256dhKey(),
                    subscription.getAuthKey());
            try {
                provider.send(recipient, payload, ttlSeconds);
                deliveredCount++;
            } catch (WebPushProviderException exception) {
                logSubscriptionFailure(subscription.getId(), exception);
                if (exception.isExpired()) {
                    subscriptionService.deactivateExpired(subscription.getId());
                    expiredCount++;
                } else if (exception.isRetryable()) {
                    retryableFailureCount++;
                } else {
                    permanentFailureCount++;
                }
            }
        }

        logDeliverySummary(subscriptions.size(), deliveredCount, expiredCount,
                retryableFailureCount, permanentFailureCount);

        if (permanentFailureCount > 0) {
            throw new ReminderDeliveryException(false, "Web Push provider rejected delivery.");
        }
        if (retryableFailureCount > 0) {
            throw new ReminderDeliveryException(true, "Web Push provider is temporarily unavailable.");
        }
    }

    private void logSubscriptionFailure(UUID subscriptionId, WebPushProviderException exception) {
        var event = LOGGER.atWarn()
                .addKeyValue("event", "push_subscription_delivery_failure")
                .addKeyValue("subscription_id", subscriptionId)
                .addKeyValue("provider_status_code", exception.getStatusCode())
                .addKeyValue("expired", exception.isExpired())
                .addKeyValue("retryable", exception.isRetryable());
        if (exception.getProviderReason() != null) {
            event.addKeyValue("provider_reason", exception.getProviderReason());
        }
        event.log("Push subscription delivery failed");
    }

    private void logDeliverySummary(
            int subscriptionCount,
            int deliveredCount,
            int expiredCount,
            int retryableFailureCount,
            int permanentFailureCount
    ) {
        boolean failed = retryableFailureCount > 0 || permanentFailureCount > 0;
        LoggingEventBuilder event = failed ? LOGGER.atWarn() : LOGGER.atInfo();
        event.addKeyValue("event", "push_reminder_delivery")
                .addKeyValue("subscription_count", subscriptionCount)
                .addKeyValue("delivered_count", deliveredCount)
                .addKeyValue("expired_count", expiredCount)
                .addKeyValue("retryable_failure_count", retryableFailureCount)
                .addKeyValue("permanent_failure_count", permanentFailureCount)
                .log(failed
                        ? "Push reminder delivery completed with failures"
                        : "Push reminder delivery completed");
    }

    private String createPayload(ReminderDelivery delivery) {
        String body = delivery.minutesBefore() == 0
                ? "Your planned session starts now."
                : "Your planned session starts in " + delivery.minutesBefore()
                        + (delivery.minutesBefore() == 1 ? " minute." : " minutes.");
        ReminderPayload payload = new ReminderPayload(
                TITLE,
                body,
                "/calendar?sessionId=" + delivery.sessionId(),
                delivery.sessionId().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ReminderDeliveryException(false, "Reminder payload could not be serialized.");
        }
    }

    private int ttlUntilSessionStart(ReminderDelivery delivery) {
        long secondsRemaining = Duration.between(clock.instant(), delivery.startsAt()).getSeconds();
        return (int) Math.max(0, Math.min(MAX_TTL_SECONDS, secondsRemaining));
    }

    private record ReminderPayload(String title, String body, String url, String sessionId) {
    }
}