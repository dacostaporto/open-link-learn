package com.openlinklearn.content.domain;

public record ContentMetadata(String title, String sourceName, String thumbnailUrl, Integer durationSeconds) {

	public ContentMetadata(String title, String sourceName, String thumbnailUrl) {
		this(title, sourceName, thumbnailUrl, null);
	}
}