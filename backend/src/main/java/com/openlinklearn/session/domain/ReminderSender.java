package com.openlinklearn.session.domain;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface ReminderSender {

    void send(ReminderDelivery delivery);

    record ReminderDelivery(UUID ownerId, UUID sessionId, Instant startsAt, int minutesBefore) { }
}