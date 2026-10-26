package com.openlinklearn.session;

import com.openlinklearn.PostgresTestDatabase;
import com.openlinklearn.session.application.AutomaticContentAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AutomaticContentAssignmentIntegrationTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SCHEMA = "assignment_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AutomaticContentAssignmentService assignmentService;

    @DynamicPropertySource
    static void configureIsolatedDatabase(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry, SCHEMA);
    }

    @BeforeEach
    void clearAssignmentRows() {
        jdbcTemplate.update("DELETE FROM reminder");
        jdbcTemplate.update("DELETE FROM learning_session");
        jdbcTemplate.update("DELETE FROM content_item");
    }

    @Test
    void doesNotSelectCompletedOrAlreadyReservedContent() {
        UUID completedContentId = insertContent("completed", "COMPLETED");
        UUID reservedContentId = insertContent("reserved", "PENDING");
        insertSession(reservedContentId);
        UUID targetSessionId = insertSession(null);

        Optional<UUID> assigned = assignmentService.assignPendingContent(targetSessionId);

        assertTrue(assigned.isEmpty());
        assertFalse(isAssigned(targetSessionId));
        assertEquals(0, countAssignments(completedContentId));
        assertEquals(1, countAssignments(reservedContentId));
    }

    @Test
    void concurrentSessionsNeverClaimTheSamePendingItem() throws Exception {
        UUID contentId = insertContent("single-pending-item", "PENDING");
        UUID firstSessionId = insertSession(null);
        UUID secondSessionId = insertSession(null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<UUID>> first = executor.submit(() -> assignAfterStart(firstSessionId, ready, start));
            Future<Optional<UUID>> second = executor.submit(() -> assignAfterStart(secondSessionId, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            Optional<UUID> firstResult = first.get(10, TimeUnit.SECONDS);
            Optional<UUID> secondResult = second.get(10, TimeUnit.SECONDS);

            assertTrue(firstResult.isPresent() ^ secondResult.isPresent());
            assertEquals(contentId, firstResult.orElseGet(secondResult::orElseThrow));
            assertEquals(1, countAssignments(contentId));
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<UUID> assignAfterStart(UUID sessionId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent assignment test did not start in time.");
        }
        return assignmentService.assignPendingContent(sessionId);
    }

    private UUID insertContent(String suffix, String status) {
        UUID contentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO content_item (id, owner_id, url, normalized_url, title, content_type, status, completed_at)
                VALUES (?, ?, ?, ?, ?, 'ARTICLE', ?, CASE WHEN ? = 'COMPLETED' THEN now() ELSE NULL END)
                """, contentId, OWNER_ID,
                "https://example.org/" + suffix,
                "https://example.org/" + suffix,
                suffix,
                status,
                status);
        return contentId;
    }

    private UUID insertSession(UUID contentId) {
        UUID sessionId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(3600);
        jdbcTemplate.update("""
                INSERT INTO learning_session (
                    id, owner_id, starts_at, ends_at, timezone, content_item_id,
                    reminder_minutes_before, assignment_cutoff_minutes_before
                ) VALUES (?, ?, ?, ?, 'Etc/UTC', ?, 10, 30)
                """, sessionId, OWNER_ID, Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)), contentId);
        return sessionId;
    }

    private boolean isAssigned(UUID sessionId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT content_item_id IS NOT NULL FROM learning_session WHERE id = ?", Boolean.class, sessionId));
    }

    private long countAssignments(UUID contentId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM learning_session WHERE content_item_id = ?", Long.class, contentId);
    }

}