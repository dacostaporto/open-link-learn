package com.openlinklearn.session.application.reminder;

import com.openlinklearn.session.domain.ReminderDeliveryException;
import com.openlinklearn.session.domain.ReminderSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "openlinklearn.push", name = "enabled", havingValue = "true")
public class ReminderJobProcessor {

    private final ReminderJobDeliveryStateService deliveryStateService;
    private final ReminderSender reminderSender;

    public ReminderJobProcessor(
            ReminderJobDeliveryStateService deliveryStateService,
            ReminderSender reminderSender
    ) {
        this.deliveryStateService = deliveryStateService;
        this.reminderSender = reminderSender;
    }

    public boolean process(ReminderJobClaimService.Claim claim, Instant now) {
        Optional<ReminderSender.ReminderDelivery> delivery = deliveryStateService.getDelivery(claim);
        if (delivery.isEmpty()) {
            return false;
        }

        try {
            reminderSender.send(delivery.get());
        } catch (ReminderDeliveryException exception) {
            deliveryStateService.recordFailure(
                    claim, now, exception.getClass().getSimpleName(), exception.isRetryable());
            return false;
        } catch (RuntimeException exception) {
            deliveryStateService.recordFailure(
                    claim, now, exception.getClass().getSimpleName(), true);
            return false;
        }
        return deliveryStateService.markSent(claim, now);
    }
}