package com.openlinklearn.content.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(description = "Partial update for editable fields on pending content. Unknown and immutable properties are rejected.")
public class UpdateContentRequest {
	private String title;
	private Integer estimatedDurationMinutes;
	private boolean titleProvided;
	private boolean estimatedDurationMinutesProvided;

	public UpdateContentRequest() {
	}

	public UpdateContentRequest(String title) {
		setTitle(title);
	}

	public UpdateContentRequest(String title, Integer estimatedDurationMinutes) {
		setTitle(title);
		setEstimatedDurationMinutes(estimatedDurationMinutes);
	}

	@JsonSetter("title")
	public void setTitle(String title) {
		this.title = title;
		this.titleProvided = true;
	}

	@Schema(description = "Replacement title; when supplied it must not be blank.", example = "A better title")
	public String getTitle() {
		return title;
	}

	@JsonSetter("estimatedDurationMinutes")
	public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) {
		this.estimatedDurationMinutes = estimatedDurationMinutes;
		this.estimatedDurationMinutesProvided = true;
	}

	@Schema(description = "Positive estimated duration in minutes; null clears the estimate and omission leaves it unchanged.", minimum = "1", example = "18")
	public Integer getEstimatedDurationMinutes() {
		return estimatedDurationMinutes;
	}

	@JsonIgnore
	public boolean hasTitle() {
		return titleProvided;
	}

	@JsonIgnore
	public boolean hasEstimatedDurationMinutes() {
		return estimatedDurationMinutesProvided;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof UpdateContentRequest that)) return false;
		return titleProvided == that.titleProvided
				&& estimatedDurationMinutesProvided == that.estimatedDurationMinutesProvided
				&& Objects.equals(title, that.title)
				&& Objects.equals(estimatedDurationMinutes, that.estimatedDurationMinutes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(title, estimatedDurationMinutes, titleProvided, estimatedDurationMinutesProvided);
	}
}