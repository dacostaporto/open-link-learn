package com.openlinklearn.session.domain;

import java.time.Instant;
import java.util.UUID;

public record UpcomingSession(UUID id, Instant startsAt, String title) {
}