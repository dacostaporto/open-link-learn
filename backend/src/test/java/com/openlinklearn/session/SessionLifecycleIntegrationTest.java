package com.openlinklearn.session;

import com.openlinklearn.PostgresTestDatabase;
import com.openlinklearn.session.application.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SessionLifecycleIntegrationTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SCHEMA = "session_lifecycle_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant STARTS_AT = Instant.parse("2026-10-26T08:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionService sessionService;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry, SCHEMA);
    }

    @BeforeEach
    void clearRows() {
        jdbcTemplate.update("DELETE FROM reminder");
        jdbcTemplate.update("DELETE FROM learning_session");
        jdbcTemplate.update("DELETE FROM content_item");
    }

    @Test
    void completesSessionAndContentTogetherAndCancelsPendingJobsIdempotently() {
        UUID contentId = insertContent();
        UUID sessionId = insertSession(contentId);
        insertJob(sessionId, "REMINDER");
        insertJob(sessionId, "ASSIGNMENT_CUTOFF");

        sessionService.completeSession(sessionId);

        assertEquals("COMPLETED", contentStatus(contentId));
        assertEquals("COMPLETED", sessionStatus(sessionId));
        assertNotNull(sessionCompletedAt(sessionId));
        assertEquals(sessionCompletedAt(sessionId), contentCompletedAt(contentId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reminder WHERE session_id = ? AND status = 'CANCELLED'",
                Long.class, sessionId));

        Instant completedAt = sessionCompletedAt(sessionId);
        sessionService.completeSession(sessionId);
        assertEquals(completedAt, sessionCompletedAt(sessionId));
    }

    @Test
    void failedSessionWriteRollsBackTheEarlierContentCompletion() {
        UUID contentId = insertContent();
        UUID sessionId = insertSession(contentId);
        installCompletionFailureTrigger();

        assertThrows(RuntimeException.class, () -> sessionService.completeSession(sessionId));

        assertEquals("PENDING", contentStatus(contentId));
        assertEquals("PLANNED", sessionStatus(sessionId));
        assertNull(contentCompletedAt(contentId));
        assertNull(sessionCompletedAt(sessionId));
        jdbcTemplate.execute("DROP TRIGGER reject_completion ON learning_session");
        jdbcTemplate.execute("DROP FUNCTION " + SCHEMA + ".reject_completion()");
    }

    @Test
    void skippingReleasesAssignedContentWithoutCompletingIt() {
        UUID contentId = insertContent();
        UUID sessionId = insertSession(contentId);

        sessionService.skipSession(sessionId);

        assertEquals("SKIPPED", sessionStatus(sessionId));
        assertNull(assignedContentId(sessionId));
        assertEquals("PENDING", contentStatus(contentId));

        UUID cancelledSessionId = insertSession(contentId);
        sessionService.cancelSession(cancelledSessionId);

        assertEquals("CANCELLED", sessionStatus(cancelledSessionId));
        assertNull(assignedContentId(cancelledSessionId));
        assertEquals("PENDING", contentStatus(contentId));
        assertNull(contentCompletedAt(contentId));
    }

    private UUID insertContent() {
        UUID contentId = UUID.randomUUID();
        String url = "https://example.test/lifecycle/" + contentId;
        jdbcTemplate.update("""
                INSERT INTO content_item (id, owner_id, url, normalized_url, title, content_type, status)
                VALUES (?, ?, ?, ?, 'Lifecycle test', 'ARTICLE', 'PENDING')
                """, contentId, OWNER_ID, url, url);
        return contentId;
    }

    private UUID insertSession(UUID contentId) {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO learning_session (
                    id, owner_id, starts_at, ends_at, timezone, content_item_id,
                    reminder_minutes_before, assignment_cutoff_minutes_before
                ) VALUES (?, ?, ?, ?, 'Etc/UTC', ?, 10, 30)
                """, sessionId, OWNER_ID, Timestamp.from(STARTS_AT),
                Timestamp.from(STARTS_AT.plusSeconds(1800)), contentId);
        return sessionId;
    }

    private void insertJob(UUID sessionId, String kind) {
        jdbcTemplate.update("""
                INSERT INTO reminder (owner_id, session_id, kind, scheduled_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?)
                """, OWNER_ID, sessionId, kind, Timestamp.from(STARTS_AT.minusSeconds(600)),
                "lifecycle:" + kind + ":" + sessionId);
    }

    private void installCompletionFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE FUNCTION %s.reject_completion() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.status = 'COMPLETED' THEN
                        RAISE EXCEPTION 'forced lifecycle test failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(SCHEMA));
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_completion BEFORE UPDATE ON learning_session
                FOR EACH ROW EXECUTE FUNCTION %s.reject_completion()
                """.formatted(SCHEMA));
    }

    private String contentStatus(UUID contentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM content_item WHERE id = ?", String.class, contentId);
    }

    private Instant contentCompletedAt(UUID contentId) {
        return jdbcTemplate.queryForObject("SELECT completed_at FROM content_item WHERE id = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1) == null
                        ? null : resultSet.getTimestamp(1).toInstant(), contentId);
    }

    private String sessionStatus(UUID sessionId) {
        return jdbcTemplate.queryForObject("SELECT status FROM learning_session WHERE id = ?",
                String.class, sessionId);
    }

    private Instant sessionCompletedAt(UUID sessionId) {
        return jdbcTemplate.queryForObject("SELECT completed_at FROM learning_session WHERE id = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1) == null
                        ? null : resultSet.getTimestamp(1).toInstant(), sessionId);
    }

    private UUID assignedContentId(UUID sessionId) {
        return jdbcTemplate.queryForObject("SELECT content_item_id FROM learning_session WHERE id = ?",
                UUID.class, sessionId);
    }
}