package com.openlinklearn.push.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO push_subscription (
                id, owner_id, endpoint, p256dh_key, auth_key, user_agent,
                created_at, last_seen_at, active
            ) VALUES (
                :id, :ownerId, :endpoint, :p256dhKey, :authKey, :userAgent,
                :now, :now, TRUE
            )
            ON CONFLICT (endpoint) DO UPDATE SET
                p256dh_key = EXCLUDED.p256dh_key,
                auth_key = EXCLUDED.auth_key,
                user_agent = EXCLUDED.user_agent,
                last_seen_at = EXCLUDED.last_seen_at,
                active = TRUE
            WHERE push_subscription.owner_id = EXCLUDED.owner_id
            """, nativeQuery = true)
    int upsertByEndpoint(
            @Param("id") UUID id,
            @Param("ownerId") UUID ownerId,
            @Param("endpoint") String endpoint,
            @Param("p256dhKey") String p256dhKey,
            @Param("authKey") String authKey,
            @Param("userAgent") String userAgent,
            @Param("now") Instant now
    );

    Optional<PushSubscription> findByOwnerIdAndEndpoint(UUID ownerId, String endpoint);

    List<PushSubscription> findByOwnerIdAndActiveTrueOrderByCreatedAtAsc(UUID ownerId);

    Optional<PushSubscription> findByIdAndOwnerId(UUID id, UUID ownerId);

    void deleteByIdAndOwnerId(UUID id, UUID ownerId);

    @Modifying
    @Query("UPDATE PushSubscription subscription SET subscription.active = false WHERE subscription.id = :id AND subscription.active = true")
    int deactivateById(@Param("id") UUID id);
}