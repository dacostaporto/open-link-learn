package com.openlinklearn.session;

import com.openlinklearn.PostgresTestDatabase;
import com.openlinklearn.session.infrastructure.ScheduledAssignmentCutoffWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AssignmentCutoffWorkerIntegrationTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00Z");
    private static final String SCHEMA = "cutoff_worker_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScheduledAssignmentCutoffWorker worker;

    @DynamicPropertySource
    static void configureIsolatedDatabase(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry, SCHEMA);
    }

    @BeforeEach
    void clearRows() {
        jdbcTemplate.update("DELETE FROM reminder");
        jdbcTemplate.update("DELETE FROM learning_session");
        jdbcTemplate.update("DELETE FROM content_item");
    }

    @Test
    void emptyBacklogClaimsNoWork() {
        assertEquals(0, worker.runDueJobs(NOW));
        assertEquals(0, reminderCount());
    }

    @Test
    void duplicateInvocationsRecordNoEligibleOutcomeOnlyOnce() {
        UUID sessionId = insertSession(NOW.plusSeconds(1800), 60);
        UUID jobId = insertDueJob(sessionId, NOW.minusSeconds(1800), "PENDING", null, null, 0);

        assertEquals(1, worker.runDueJobs(NOW));
        assertEquals(0, worker.runDueJobs(NOW));

        assertEquals("SENT", jobStatus(jobId));
        assertEquals("NO_ELIGIBLE_CONTENT", jobOutcome(jobId));
        assertEquals(1, jobAttempts(jobId));
    }

    @Test
    void concurrentWorkersClaimOneJobAndAssignItsContentOnce() throws Exception {
        UUID contentId = insertContent();
        UUID sessionId = insertSession(NOW.plusSeconds(3599), 60);
        UUID jobId = insertDueJob(sessionId, NOW.minusSeconds(1), "PENDING", null, null, 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(() -> runAfterStart(ready, start));
            Future<Integer> second = executor.submit(() -> runAfterStart(ready, start));
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, first.get(10, java.util.concurrent.TimeUnit.SECONDS)
                    + second.get(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals("SENT", jobStatus(jobId));
        assertEquals("ASSIGNED", jobOutcome(jobId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM learning_session WHERE content_item_id = ?", Long.class, contentId));
    }

    @Test
    void staleProcessingClaimIsRecoveredAndRetriedWithoutDuplicateAssignment() {
        UUID existingContentId = insertContent();
        UUID pendingContentId = insertContent();
        UUID sessionId = insertSession(NOW.plusSeconds(1800), 60);
        jdbcTemplate.update("UPDATE learning_session SET content_item_id = ? WHERE id = ?",
            existingContentId, sessionId);
        UUID jobId = insertDueJob(sessionId, NOW.minusSeconds(1800), "PROCESSING",
                NOW.minusSeconds(600), UUID.randomUUID(), 1);

        assertEquals(0, worker.runDueJobs(NOW));
        assertEquals("PENDING", jobStatus(jobId));
        assertEquals(1, jobAttempts(jobId));

        assertEquals(1, worker.runDueJobs(NOW.plusSeconds(6)));
        assertEquals("SENT", jobStatus(jobId));
        assertEquals("ASSIGNED", jobOutcome(jobId));
        assertEquals(2, jobAttempts(jobId));
        assertEquals(existingContentId, jdbcTemplate.queryForObject(
            "SELECT content_item_id FROM learning_session WHERE id = ?", UUID.class, sessionId));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT count(*) FROM learning_session WHERE content_item_id = ?", Long.class, pendingContentId));
    }

    private int runAfterStart(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent worker test did not start in time.");
        }
        return worker.runDueJobs(NOW);
    }

    private UUID insertSession(Instant startsAt, int cutoffMinutes) {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO learning_session (
                    id, owner_id, starts_at, ends_at, timezone, status,
                    reminder_minutes_before, assignment_cutoff_minutes_before
                ) VALUES (?, ?, ?, ?, 'Etc/UTC', 'PLANNED', 10, ?)
                """, sessionId, OWNER_ID, Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)),
                cutoffMinutes);
        return sessionId;
    }

    private UUID insertDueJob(
            UUID sessionId,
            Instant scheduledAt,
            String status,
            Instant processingStartedAt,
            UUID claimToken,
            int attemptCount
    ) {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reminder (
                    id, owner_id, session_id, kind, scheduled_at, status, attempt_count,
                    processing_started_at, claim_token, idempotency_key
                ) VALUES (?, ?, ?, 'ASSIGNMENT_CUTOFF', ?, ?, ?, ?, ?, ?)
                """, jobId, OWNER_ID, sessionId, Timestamp.from(scheduledAt), status, attemptCount,
                processingStartedAt == null ? null : Timestamp.from(processingStartedAt), claimToken,
                "worker-test:" + jobId);
        return jobId;
    }

    private UUID insertContent() {
        UUID contentId = UUID.randomUUID();
        String url = "https://example.org/worker-" + contentId;
        jdbcTemplate.update("""
                INSERT INTO content_item (id, owner_id, url, normalized_url, title, content_type, status)
                VALUES (?, ?, ?, ?, 'Worker test item', 'ARTICLE', 'PENDING')
                """, contentId, OWNER_ID, url, url);
        return contentId;
    }

    private long reminderCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM reminder", Long.class);
    }

    private String jobStatus(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT status FROM reminder WHERE id = ?", String.class, jobId);
    }

    private String jobOutcome(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT outcome FROM reminder WHERE id = ?", String.class, jobId);
    }

    private int jobAttempts(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT attempt_count FROM reminder WHERE id = ?", Integer.class, jobId);
    }

}