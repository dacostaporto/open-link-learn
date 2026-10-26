package com.openlinklearn.session.application.dto;

import com.openlinklearn.content.domain.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Content summary nested in a session response.")
public record SessionContentResponse(
	@Schema(description = "Content item identifier") UUID id,
	@Schema(description = "Display title") String title,
	@Schema(description = "Content URL") String url,
	@Schema(description = "Detected content type") ContentType type
) {
}