package com.openlinklearn.session.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Schema(description = "Partial update for a planned one-off session. Omitted fields remain unchanged; a null contentItemId removes its assignment.")
public class UpdateSessionRequest {

    private Instant startsAt;
    private Integer durationMinutes;
    private UUID contentItemId;
    private Integer reminderMinutesBefore;
    private Integer assignmentCutoffMinutesBefore;
    private boolean startsAtProvided;
    private boolean durationMinutesProvided;
    private boolean contentItemIdProvided;
    private boolean reminderMinutesBeforeProvided;
    private boolean assignmentCutoffMinutesBeforeProvided;

    public UpdateSessionRequest() {
    }

    @JsonSetter("startsAt")
    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
        this.startsAtProvided = true;
    }

    @Schema(description = "New UTC start instant.", example = "2026-10-26T07:00:00Z")
    public Instant getStartsAt() {
        return startsAt;
    }

    @JsonSetter("durationMinutes")
    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
        this.durationMinutesProvided = true;
    }

    @Schema(description = "Session duration in minutes; allowed range is 1-720.", minimum = "1", maximum = "720", example = "25")
    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    @JsonSetter("contentItemId")
    public void setContentItemId(UUID contentItemId) {
        this.contentItemId = contentItemId;
        this.contentItemIdProvided = true;
    }

    @Schema(description = "Pending content item to assign; null removes the current assignment.")
    public UUID getContentItemId() {
        return contentItemId;
    }

    @JsonSetter("reminderMinutesBefore")
    public void setReminderMinutesBefore(Integer reminderMinutesBefore) {
        this.reminderMinutesBefore = reminderMinutesBefore;
        this.reminderMinutesBeforeProvided = true;
    }

    @Schema(description = "Reminder lead time in minutes; allowed range is 0-1440.", minimum = "0", maximum = "1440")
    public Integer getReminderMinutesBefore() {
        return reminderMinutesBefore;
    }

    @JsonSetter("assignmentCutoffMinutesBefore")
    public void setAssignmentCutoffMinutesBefore(Integer assignmentCutoffMinutesBefore) {
        this.assignmentCutoffMinutesBefore = assignmentCutoffMinutesBefore;
        this.assignmentCutoffMinutesBeforeProvided = true;
    }

    @Schema(description = "Assignment cutoff in minutes before the session; allowed range is 0-10080.", minimum = "0", maximum = "10080")
    public Integer getAssignmentCutoffMinutesBefore() {
        return assignmentCutoffMinutesBefore;
    }

    @JsonIgnore
    public boolean hasStartsAt() {
        return startsAtProvided;
    }

    @JsonIgnore
    public boolean hasDurationMinutes() {
        return durationMinutesProvided;
    }

    @JsonIgnore
    public boolean hasContentItemId() {
        return contentItemIdProvided;
    }

    @JsonIgnore
    public boolean hasReminderMinutesBefore() {
        return reminderMinutesBeforeProvided;
    }

    @JsonIgnore
    public boolean hasAssignmentCutoffMinutesBefore() {
        return assignmentCutoffMinutesBeforeProvided;
    }

    @JsonIgnore
    public boolean hasChanges() {
        return startsAtProvided || durationMinutesProvided || contentItemIdProvided
                || reminderMinutesBeforeProvided || assignmentCutoffMinutesBeforeProvided;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UpdateSessionRequest that)) return false;
        return startsAtProvided == that.startsAtProvided
                && durationMinutesProvided == that.durationMinutesProvided
                && contentItemIdProvided == that.contentItemIdProvided
                && reminderMinutesBeforeProvided == that.reminderMinutesBeforeProvided
                && assignmentCutoffMinutesBeforeProvided == that.assignmentCutoffMinutesBeforeProvided
                && Objects.equals(startsAt, that.startsAt)
                && Objects.equals(durationMinutes, that.durationMinutes)
                && Objects.equals(contentItemId, that.contentItemId)
                && Objects.equals(reminderMinutesBefore, that.reminderMinutesBefore)
                && Objects.equals(assignmentCutoffMinutesBefore, that.assignmentCutoffMinutesBefore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startsAt, durationMinutes, contentItemId, reminderMinutesBefore,
                assignmentCutoffMinutesBefore, startsAtProvided, durationMinutesProvided,
                contentItemIdProvided, reminderMinutesBeforeProvided, assignmentCutoffMinutesBeforeProvided);
    }
}