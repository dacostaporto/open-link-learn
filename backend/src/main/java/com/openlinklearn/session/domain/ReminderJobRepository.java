package com.openlinklearn.session.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ReminderJobRepository extends JpaRepository<ReminderJob, UUID> {

      boolean existsBySessionIdAndKindAndScheduledAt(
        UUID sessionId,
        ReminderJob.Kind kind,
        Instant scheduledAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM ReminderJob job WHERE job.id = :id")
    Optional<ReminderJob> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ReminderJob> findBySessionIdAndKindAndStatusIn(
        UUID sessionId,
        ReminderJob.Kind kind,
        Set<ReminderJob.Status> statuses);

    @Query(value = """
        SELECT * FROM reminder
        WHERE kind = 'ASSIGNMENT_CUTOFF'
          AND status = 'PROCESSING'
          AND processing_started_at <= :staleBefore
        ORDER BY processing_started_at ASC, id ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ReminderJob> lockStaleAssignmentCutoffJobs(
        @Param("staleBefore") Instant staleBefore,
        @Param("batchSize") int batchSize);

    @Query(value = """
        SELECT * FROM reminder
        WHERE kind = 'ASSIGNMENT_CUTOFF'
          AND status = 'PENDING'
          AND scheduled_at <= :now
          AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
        ORDER BY scheduled_at ASC, id ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ReminderJob> lockDueAssignmentCutoffJobs(
        @Param("now") Instant now,
        @Param("batchSize") int batchSize);

    @Query(value = """
      SELECT * FROM reminder
      WHERE kind = 'REMINDER'
        AND status = 'PROCESSING'
        AND processing_started_at <= :staleBefore
      ORDER BY processing_started_at ASC, id ASC
      LIMIT :batchSize
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
    List<ReminderJob> lockStaleReminderJobs(
      @Param("staleBefore") Instant staleBefore,
      @Param("batchSize") int batchSize);

    @Query(value = """
      SELECT * FROM reminder
      WHERE kind = 'REMINDER'
        AND status = 'PENDING'
        AND scheduled_at <= :now
        AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
      ORDER BY scheduled_at ASC, id ASC
      LIMIT :batchSize
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
    List<ReminderJob> lockDueReminderJobs(
      @Param("now") Instant now,
      @Param("batchSize") int batchSize);
}