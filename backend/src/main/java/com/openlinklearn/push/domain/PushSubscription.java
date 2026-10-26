package com.openlinklearn.push.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_subscription")
public class PushSubscription {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, columnDefinition = "text")
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, columnDefinition = "text")
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, columnDefinition = "text")
    private String authKey;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private boolean active;

    protected PushSubscription() {
    }

    public PushSubscription(
            UUID id,
            UUID ownerId,
            String endpoint,
            String p256dhKey,
            String authKey,
            String userAgent,
            Instant createdAt,
            Instant lastSeenAt,
            boolean active
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dhKey() {
        return p256dhKey;
    }

    public String getAuthKey() {
        return authKey;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        active = false;
    }
}