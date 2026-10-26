package com.openlinklearn.session.domain;

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
@Table(name = "reminder")
public class ReminderJob {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Kind kind;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(length = 32)
    private String outcome;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReminderJob() {
    }

    public ReminderJob(UUID ownerId, UUID sessionId, Kind kind, Instant scheduledAt, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.sessionId = sessionId;
        this.kind = kind;
        this.scheduledAt = scheduledAt;
        this.idempotencyKey = idempotencyKey;
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

    public UUID getSessionId() {
        return sessionId;
    }

    public Kind getKind() {
        return kind;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getLastError() {
        return lastError;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public boolean isClaimedBy(UUID token) {
        return status == Status.PROCESSING && token != null && token.equals(claimToken);
    }

    public String getOutcome() {
        return outcome;
    }

    public Status getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void cancel() {
        if (status == Status.PENDING || status == Status.PROCESSING) {
            status = Status.CANCELLED;
            clearClaim();
        }
    }

    public void claim(UUID token, Instant now) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Only pending jobs can be claimed.");
        }
        status = Status.PROCESSING;
        attemptCount++;
        processingStartedAt = now;
        claimToken = token;
        nextAttemptAt = null;
        lastError = null;
    }

    public void markProcessed(Outcome processedOutcome, Instant now) {
        if (status == Status.PENDING || status == Status.PROCESSING) {
            status = Status.SENT;
            sentAt = now;
            outcome = processedOutcome.name();
            nextAttemptAt = null;
            lastError = null;
            clearClaim();
        }
    }

    public void markSent(Instant now) {
        if (status == Status.PROCESSING) {
            status = Status.SENT;
            sentAt = now;
            nextAttemptAt = null;
            lastError = null;
            clearClaim();
        }
    }

    public void markPermanentlyFailed(String safeError) {
        if (status == Status.PROCESSING) {
            status = Status.FAILED;
            lastError = truncate(safeError);
            nextAttemptAt = null;
            clearClaim();
        }
    }

    public void recoverStaleClaim(Instant now) {
        if (status == Status.PROCESSING) {
            retryOrFail(now, "Processing lease expired.");
        }
    }

    public void recordFailure(Instant now, String safeError) {
        if (status == Status.PROCESSING) {
            retryOrFail(now, safeError);
        }
    }

    private void retryOrFail(Instant now, String safeError) {
        lastError = truncate(safeError);
        clearClaim();
        if (attemptCount >= 10) {
            status = Status.FAILED;
            nextAttemptAt = null;
            return;
        }
        status = Status.PENDING;
        nextAttemptAt = now.plusSeconds(Math.min(300, 5L << Math.min(Math.max(attemptCount - 1, 0), 6)));
    }

    private String truncate(String safeError) {
        return safeError.length() <= 500 ? safeError : safeError.substring(0, 500);
    }

    private void clearClaim() {
        processingStartedAt = null;
        claimToken = null;
    }

    public enum Kind {
        REMINDER,
        ASSIGNMENT_CUTOFF
    }

    public enum Status {
        PENDING,
        PROCESSING,
        SENT,
        FAILED,
        CANCELLED
    }

    public enum Outcome {
        ASSIGNED,
        NO_ELIGIBLE_CONTENT
    }
}