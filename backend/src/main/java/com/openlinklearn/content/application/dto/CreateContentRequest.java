package com.openlinklearn.content.application.dto;

import com.openlinklearn.content.domain.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to add supported HTTP(S) content to the local backlog.")
public record CreateContentRequest(
		@Schema(description = "Supported YouTube or article URL; localhost and private-network targets are rejected.", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
		String url,
		@Schema(description = "Optional title override. When omitted or blank, a hostname-based fallback is used.", example = "English listening practice")
		String title,
		@Schema(description = "Optional override for the URL-based type detection.", example = "ARTICLE")
		ContentType type
) {
	public CreateContentRequest(String url, String title) {
		this(url, title, null);
	}
}