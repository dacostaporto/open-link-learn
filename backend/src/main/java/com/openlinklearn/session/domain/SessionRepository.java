package com.openlinklearn.session.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<LearningSession, UUID> {
    Optional<LearningSession> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM LearningSession session WHERE session.id = :id AND session.ownerId = :ownerId")
    Optional<LearningSession> findByIdAndOwnerIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    Optional<LearningSession> findBySeriesIdAndSeriesOccurrenceDate(UUID seriesId, LocalDate occurrenceDate);

        long countByOwnerIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndStatusIn(
            UUID ownerId, Instant from, Instant to, Set<SessionStatus> statuses);

        long countByOwnerIdAndStartsAtGreaterThanEqualAndStatusAndContentItemIsNull(
            UUID ownerId, Instant from, SessionStatus status);

        @Query("""
            SELECT new com.openlinklearn.session.domain.UpcomingSession(
            session.id, session.startsAt, COALESCE(content.title, 'Unassigned session'))
            FROM LearningSession session
            LEFT JOIN session.contentItem content
            WHERE session.ownerId = :ownerId
              AND session.status = :status
              AND session.startsAt >= :from
            ORDER BY session.startsAt ASC
            """)
    List<UpcomingSession> findNextUpcomingByOwner(
            @Param("ownerId") UUID ownerId,
            @Param("status") SessionStatus status,
            @Param("from") Instant from,
            Pageable pageable
        );

        @Query("""
            SELECT session FROM LearningSession session
            WHERE session.ownerId = :ownerId
              AND session.startsAt < :to
              AND session.endsAt > :from
            ORDER BY session.startsAt ASC
            """)
        List<LearningSession> findOverlappingByOwner(
            @Param("ownerId") UUID ownerId,
            @Param("from") Instant from,
            @Param("to") Instant to
        );

    boolean existsByOwnerIdAndContentItem_IdAndStatusIn(UUID ownerId, UUID contentItemId, Set<SessionStatus> statuses);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        List<LearningSession> findAllBySeriesIdAndStatusAndStartsAtAfterOrderBySeriesOccurrenceDateAsc(
            UUID seriesId,
            SessionStatus status,
            Instant startsAt
        );

    @Modifying
    @Query(value = """
            INSERT INTO learning_session (
                owner_id, starts_at, ends_at, timezone, reminder_minutes_before,
                assignment_cutoff_minutes_before, series_id, series_occurrence_date
            ) VALUES (
                :ownerId, :startsAt, :endsAt, :timezone, :reminderMinutesBefore,
                :assignmentCutoffMinutesBefore, :seriesId, :occurrenceDate
            )
            ON CONFLICT (series_id, series_occurrence_date) DO NOTHING
            """, nativeQuery = true)
    int insertOccurrenceIfAbsent(
            @Param("ownerId") UUID ownerId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("timezone") String timezone,
            @Param("reminderMinutesBefore") int reminderMinutesBefore,
            @Param("assignmentCutoffMinutesBefore") int assignmentCutoffMinutesBefore,
            @Param("seriesId") UUID seriesId,
            @Param("occurrenceDate") LocalDate occurrenceDate
    );
}