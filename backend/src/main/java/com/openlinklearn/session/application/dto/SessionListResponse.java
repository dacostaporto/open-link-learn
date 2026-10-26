package com.openlinklearn.session.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Sessions overlapping the requested time range.")
public record SessionListResponse(
	@Schema(description = "Matching sessions") List<SessionResponse> items
) {
}