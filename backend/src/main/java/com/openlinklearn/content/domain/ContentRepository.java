package com.openlinklearn.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<ContentItem, UUID> {
        long countByOwnerIdAndStatus(UUID ownerId, ContentStatus status);

    boolean existsByOwnerIdAndNormalizedUrl(UUID ownerId, String normalizedUrl);

    Optional<ContentItem> findByIdAndOwnerId(UUID id, UUID ownerId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT item FROM ContentItem item WHERE item.id = :id AND item.ownerId = :ownerId")
        Optional<ContentItem> findByIdAndOwnerIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

                @Query(value = """
                                                SELECT item.*
                                                FROM content_item item
                                                WHERE item.owner_id = :ownerId
                                                        AND item.status = 'PENDING'
                                                        AND NOT EXISTS (
                                                                        SELECT 1 FROM learning_session session
                                                                        WHERE session.content_item_id = item.id
                                                                                AND session.status IN ('PLANNED', 'IN_PROGRESS')
                                                        )
                                                ORDER BY random()
                                                LIMIT 1
                                                FOR UPDATE OF item SKIP LOCKED
                                                """, nativeQuery = true)
                Optional<ContentItem> findRandomPendingUnassignedForUpdate(@Param("ownerId") UUID ownerId);

    @Query("""
            SELECT item FROM ContentItem item
            WHERE item.ownerId = :ownerId
              AND (:status IS NULL OR item.status = :status)
              AND (:type IS NULL OR item.contentType = :type)
              AND (:search IS NULL
                   OR LOWER(item.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(item.sourceName, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<ContentItem> searchByOwner(
            @Param("ownerId") UUID ownerId,
            @Param("status") ContentStatus status,
            @Param("type") ContentType type,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            SELECT item FROM ContentItem item
            WHERE item.ownerId = :ownerId
              AND item.status = :status
              AND (:search IS NULL
                   OR LOWER(item.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(item.sourceName, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<ContentItem> searchCompletedByOwner(
            @Param("ownerId") UUID ownerId,
            @Param("status") ContentStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}