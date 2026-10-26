package com.openlinklearn.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_item")
public class ContentItem {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "normalized_url", nullable = false, columnDefinition = "text")
    private String normalizedUrl;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType;

    @Column(name = "thumbnail_url", columnDefinition = "text")
    private String thumbnailUrl;

    @Column(name = "source_name", columnDefinition = "text")
    private String sourceName;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ContentItem() {
    }

    public ContentItem(
            UUID ownerId,
            String url,
            String normalizedUrl,
            String title,
            ContentType contentType,
            String sourceName
    ) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.url = url;
        this.normalizedUrl = normalizedUrl;
        this.title = title;
        this.contentType = contentType;
        this.sourceName = sourceName;
        this.status = ContentStatus.PENDING;
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

    public String getUrl() {
        return url;
    }

    public String getNormalizedUrl() {
        return normalizedUrl;
    }

    public String getTitle() {
        return title;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getSourceName() {
        return sourceName;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public void updateEstimatedDurationMinutes(Integer estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public void markCompleted(Instant completedAt) {
        if (status == ContentStatus.PENDING) {
            status = ContentStatus.COMPLETED;
            this.completedAt = completedAt;
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}