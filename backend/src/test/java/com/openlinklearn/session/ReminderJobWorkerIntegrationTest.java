package com.openlinklearn.session;

import com.openlinklearn.PostgresTestDatabase;
import com.openlinklearn.session.application.reminder.ReminderJobClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReminderJobWorkerIntegrationTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00Z");
    private static final String SCHEMA = "reminder_worker_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReminderJobClaimService claimService;

    @DynamicPropertySource
    static void configureIsolatedDatabase(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry, SCHEMA);
    }

    @BeforeEach
    void clearRows() {
        jdbcTemplate.update("DELETE FROM reminder");
        jdbcTemplate.update("DELETE FROM learning_session");
    }

    @Test
    void concurrentWorkersClaimOnlyOneDueReminder() throws Exception {
        UUID sessionId = insertSession();
        UUID jobId = insertJob(sessionId, "PENDING", NOW.minusSeconds(1), null, null, 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<ReminderJobClaimService.Claim>> first = executor.submit(() -> claimAfterStart(ready, start));
            Future<List<ReminderJobClaimService.Claim>> second = executor.submit(() -> claimAfterStart(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, first.get(10, TimeUnit.SECONDS).size() + second.get(10, TimeUnit.SECONDS).size());
        } finally {
            executor.shutdownNow();
        }

        assertEquals("PROCESSING", status(jobId));
        assertEquals(1, attempts(jobId));
        assertTrue(claimToken(jobId) != null);
    }

    @Test
    void duplicateClaimInvocationDoesNotReclaimProcessingReminder() {
        UUID jobId = insertJob(insertSession(), "PENDING", NOW.minusSeconds(1), null, null, 0);

        assertEquals(1, claimService.recoverAndClaim(NOW, NOW.minusSeconds(300), 50).size());
        assertEquals(0, claimService.recoverAndClaim(NOW, NOW.minusSeconds(300), 50).size());

        assertEquals("PROCESSING", status(jobId));
        assertEquals(1, attempts(jobId));
    }

    @Test
    void staleProcessingClaimRecoversThenWaitsForBackoffBeforeRetry() {
        UUID jobId = insertJob(insertSession(), "PROCESSING", NOW.minusSeconds(60),
                NOW.minusSeconds(301), UUID.randomUUID(), 1);

        assertEquals(0, claimService.recoverAndClaim(NOW, NOW.minusSeconds(300), 50).size());
        assertEquals("PENDING", status(jobId));
        assertEquals(Timestamp.from(NOW.plusSeconds(5)), nextAttemptAt(jobId));
        assertEquals(1, attempts(jobId));

        List<ReminderJobClaimService.Claim> retry = claimService.recoverAndClaim(
                NOW.plusSeconds(5), NOW.minusSeconds(295), 50);
        assertEquals(1, retry.size());
        assertEquals("PROCESSING", status(jobId));
        assertEquals(2, attempts(jobId));
    }

    private List<ReminderJobClaimService.Claim> claimAfterStart(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent reminder claim test did not start in time.");
        }
        return claimService.recoverAndClaim(NOW, NOW.minusSeconds(300), 50);
    }

    private UUID insertSession() {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO learning_session (
                    id, owner_id, starts_at, ends_at, timezone, status,
                    reminder_minutes_before, assignment_cutoff_minutes_before
                ) VALUES (?, ?, ?, ?, 'Etc/UTC', 'PLANNED', 10, 30)
                """, sessionId, OWNER_ID, Timestamp.from(NOW.plusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(5400)));
        return sessionId;
    }

    private UUID insertJob(
            UUID sessionId,
            String jobStatus,
            Instant scheduledAt,
            Instant processingStartedAt,
            UUID claimToken,
            int attemptCount
    ) {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reminder (
                    id, owner_id, session_id, kind, scheduled_at, status, attempt_count,
                    processing_started_at, claim_token, idempotency_key
                ) VALUES (?, ?, ?, 'REMINDER', ?, ?, ?, ?, ?, ?)
                """, jobId, OWNER_ID, sessionId, Timestamp.from(scheduledAt), jobStatus, attemptCount,
                processingStartedAt == null ? null : Timestamp.from(processingStartedAt), claimToken,
                "reminder-worker-test:" + jobId);
        return jobId;
    }

    private String status(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT status FROM reminder WHERE id = ?", String.class, jobId);
    }

    private Integer attempts(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT attempt_count FROM reminder WHERE id = ?", Integer.class, jobId);
    }

    private UUID claimToken(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT claim_token FROM reminder WHERE id = ?", UUID.class, jobId);
    }

    private Timestamp nextAttemptAt(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT next_attempt_at FROM reminder WHERE id = ?", Timestamp.class, jobId);
    }

}