package com.openlinklearn.push.application.dto;

import java.time.Instant;
import java.util.UUID;

public record PushSubscriptionResponse(UUID id, boolean active, Instant createdAt) {
}