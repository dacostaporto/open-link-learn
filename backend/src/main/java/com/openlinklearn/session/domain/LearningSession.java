package com.openlinklearn.session.domain;

import com.openlinklearn.content.domain.ContentItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "learning_session")
public class LearningSession {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false, length = 64)
    private String timezone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_item_id")
    private ContentItem contentItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status;

    @Column(name = "reminder_minutes_before", nullable = false)
    private int reminderMinutesBefore;

    @Column(name = "assignment_cutoff_minutes_before", nullable = false)
    private int assignmentCutoffMinutesBefore;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "series_id", updatable = false)
    private UUID seriesId;

    @Column(name = "series_occurrence_date", updatable = false)
    private LocalDate seriesOccurrenceDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LearningSession() {
    }

    public LearningSession(
            UUID ownerId,
            Instant startsAt,
            Instant endsAt,
            String timezone,
            ContentItem contentItem,
            int reminderMinutesBefore,
            int assignmentCutoffMinutesBefore
    ) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.timezone = timezone;
        this.contentItem = contentItem;
        this.reminderMinutesBefore = reminderMinutesBefore;
        this.assignmentCutoffMinutesBefore = assignmentCutoffMinutesBefore;
        this.status = SessionStatus.PLANNED;
    }

    @PrePersist
    void setCreatedTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void setUpdatedTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getTimezone() {
        return timezone;
    }

    public ContentItem getContentItem() {
        return contentItem;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public int getReminderMinutesBefore() {
        return reminderMinutesBefore;
    }

    public int getAssignmentCutoffMinutesBefore() {
        return assignmentCutoffMinutesBefore;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getSeriesId() {
        return seriesId;
    }

    public LocalDate getSeriesOccurrenceDate() {
        return seriesOccurrenceDate;
    }

    public void updateSchedule(Instant startsAt, Instant endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void updateSchedule(Instant startsAt, Instant endsAt, String timezone) {
        updateSchedule(startsAt, endsAt);
        this.timezone = timezone;
    }

    public void updateContentItem(ContentItem contentItem) {
        this.contentItem = contentItem;
    }

    public void updateReminderMinutesBefore(int reminderMinutesBefore) {
        this.reminderMinutesBefore = reminderMinutesBefore;
    }

    public void updateAssignmentCutoffMinutesBefore(int assignmentCutoffMinutesBefore) {
        this.assignmentCutoffMinutesBefore = assignmentCutoffMinutesBefore;
    }

    public void markCompleted(Instant completedAt) {
        if (status != SessionStatus.COMPLETED) {
            status = SessionStatus.COMPLETED;
            this.completedAt = completedAt;
        }
    }

    public void skipAndReleaseContent() {
        status = SessionStatus.SKIPPED;
        contentItem = null;
    }

    public void cancelAndReleaseContent() {
        status = SessionStatus.CANCELLED;
        contentItem = null;
    }
}