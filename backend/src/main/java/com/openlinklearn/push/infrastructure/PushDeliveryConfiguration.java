package com.openlinklearn.push.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openlinklearn.push.application.PushSubscriptionService;
import com.openlinklearn.push.domain.PushSubscriptionRepository;
import com.openlinklearn.session.domain.ReminderSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "openlinklearn.push", name = "enabled", havingValue = "true")
public class PushDeliveryConfiguration {

    @Bean
    WebPushProvider webPushProvider(VapidProperties properties) {
        return new VapidWebPushProvider(properties);
    }

    @Bean
    ReminderSender reminderSender(
            WebPushProvider provider,
            PushSubscriptionRepository subscriptionRepository,
            PushSubscriptionService subscriptionService,
            ObjectMapper objectMapper,
            Clock reminderDeliveryClock
    ) {
        return new VapidReminderSender(
                provider,
                subscriptionRepository,
                subscriptionService,
                objectMapper,
                reminderDeliveryClock);
    }
}