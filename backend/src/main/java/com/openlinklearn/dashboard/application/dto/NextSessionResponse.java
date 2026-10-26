package com.openlinklearn.dashboard.application.dto;

import java.time.Instant;
import java.util.UUID;

public record NextSessionResponse(UUID id, Instant startsAt, String title) {
}