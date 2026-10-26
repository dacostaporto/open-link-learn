package com.openlinklearn;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSeriesMigrationTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BASE_SCHEMA_VERSION = "1";
    private final List<String> testSchemas = new ArrayList<>();

    @AfterEach
    void dropTestSchemas() throws SQLException {
        for (String schema : testSchemas) {
            PostgresTestDatabase.dropSchema(schema);
        }
    }

    @Test
    void migratesAnEmptyDatabaseAndEnforcesSeriesAndOccurrenceConstraints() throws Exception {
        String schema = newSchema();
        Flyway flyway = flyway(schema);
        flyway.migrate();
        assertEquals(BASE_SCHEMA_VERSION, flyway.info().current().getVersion().getVersion());

        UUID seriesId = insertSeries(schema, LocalDate.parse("2026-01-05"), null, 60, "{1,3,5}", null, null);
        assertNotNull(seriesId);
        insertSeries(schema, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-02-05"), 720, "{7}", 0, 10080);

        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 0, "{1}", 10, 30));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 721, "{1}", 10, 30));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-02-05"), LocalDate.parse("2026-02-04"), 60, "{1}", 10, 30));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 60, "{}", 10, 30));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 60, "{0}", 10, 30));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 60, "{8}", 10, 30));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 60, "{2,2}", 10, 30));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 60, "{1}", -1, 30));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 60, "{1}", 1441, 30));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 60, "{1}", 10, -1));
        assertCheckViolation(() -> insertSeries(schema, LocalDate.parse("2026-01-05"), null, 60, "{1}", 10, 10081));

        insertOccurrence(schema, seriesId, LocalDate.parse("2026-01-05"));
        assertUniqueViolation(() -> insertOccurrence(schema, seriesId, LocalDate.parse("2026-01-05")));
        assertCheckViolation(() -> insertOccurrence(schema, seriesId, null));
        assertCheckViolation(() -> insertOccurrence(schema, null, LocalDate.parse("2026-01-05")));
        assertForeignKeyViolation(() -> insertOccurrence(schema, UUID.randomUUID(), LocalDate.parse("2026-01-06")));
    }

    @Test
    void enforcesContentAndSessionConstraints() throws Exception {
        String schema = newSchema();
        Flyway flyway = flyway(schema);
        flyway.migrate();
        assertEquals(BASE_SCHEMA_VERSION, flyway.info().current().getVersion().getVersion());

        UUID contentId = insertContent(schema, OWNER_ID, "https://example.test/one", "https://example.test/one",
            "PENDING", 30);
        assertUniqueViolation(() -> insertContent(schema, OWNER_ID, "https://example.test/two",
            "https://example.test/one", "PENDING", 30));
        assertCheckViolation(() -> insertContent(schema, OWNER_ID, "https://example.test/invalid-status",
            "https://example.test/invalid-status", "ARCHIVED", 30));
        assertCheckViolation(() -> insertContent(schema, OWNER_ID, "https://example.test/invalid-duration",
            "https://example.test/invalid-duration", "PENDING", 0));

        insertSessionWithContent(schema, OWNER_ID, contentId,
            "2026-01-05 08:00:00Z", "2026-01-05 09:00:00Z");
        assertCheckViolation(() -> insertSessionWithContent(schema, OWNER_ID, null,
            "2026-01-05 09:00:00Z", "2026-01-05 08:00:00Z"));
        assertUniqueViolation(() -> insertSessionWithContent(schema, OWNER_ID, contentId,
            "2026-01-06 08:00:00Z", "2026-01-06 09:00:00Z"));
        assertHistoryIndexUsed(schema);
    }

    @Test
    void createsReminderJobsWithConstraintsAndIndexedDueQueries() throws Exception {
        String schema = newSchema();
        Flyway flyway = flyway(schema);
        flyway.migrate();
        assertEquals(BASE_SCHEMA_VERSION, flyway.info().current().getVersion().getVersion());

        UUID sessionId = insertSession(schema, OWNER_ID);
        UUID pendingId = insertReminder(schema, OWNER_ID, sessionId, "REMINDER", "PENDING",
            "2026-01-05 08:00:00Z", null, 0, "reminder:pending");
        UUID retryId = insertReminder(schema, OWNER_ID, sessionId, "ASSIGNMENT_CUTOFF", "FAILED",
            "2026-01-05 07:00:00Z", "2026-01-05 08:10:00Z", 2, "cutoff:retry");
        insertReminder(schema, OWNER_ID, sessionId, "REMINDER", "FAILED",
            "2026-01-05 07:00:00Z", "2026-01-05 08:30:00Z", 2, "reminder:future-retry");

        assertForeignKeyViolation(() -> insertReminder(schema, OWNER_ID, UUID.randomUUID(), "REMINDER", "PENDING",
            "2026-01-05 08:00:00Z", null, 0, "missing-session"));
        assertForeignKeyViolation(() -> insertReminder(schema, UUID.randomUUID(), sessionId, "REMINDER", "PENDING",
            "2026-01-05 08:00:00Z", null, 0, "missing-owner"));
        assertCheckViolation(() -> insertReminder(schema, OWNER_ID, sessionId, "UNKNOWN", "PENDING",
            "2026-01-05 08:00:00Z", null, 0, "invalid-kind"));
        assertCheckViolation(() -> insertReminder(schema, OWNER_ID, sessionId, "REMINDER", "UNKNOWN",
            "2026-01-05 08:00:00Z", null, 0, "invalid-status"));
        assertCheckViolation(() -> insertReminder(schema, OWNER_ID, sessionId, "REMINDER", "PENDING",
            "2026-01-05 08:00:00Z", null, -1, "negative-attempts"));
        assertCheckViolation(() -> insertReminder(schema, OWNER_ID, sessionId, "REMINDER", "PENDING",
            "2026-01-05 08:00:00Z", null, 11, "too-many-attempts"));
        assertUniqueViolation(() -> insertReminder(schema, OWNER_ID, sessionId, "REMINDER", "PENDING",
            "2026-01-05 08:00:00Z", null, 0, "reminder:pending"));

        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT id FROM reminder
                 WHERE (status = 'PENDING' AND scheduled_at <= TIMESTAMPTZ '2026-01-05 08:15:00Z')
                OR (status = 'FAILED' AND next_attempt_at <= TIMESTAMPTZ '2026-01-05 08:15:00Z')
                 ORDER BY id
                 """)) {
            try (ResultSet result = statement.executeQuery()) {
            List<UUID> dueIds = new ArrayList<>();
            while (result.next()) dueIds.add(result.getObject("id", UUID.class));
                assertEquals(2, dueIds.size());
                assertTrue(dueIds.containsAll(List.of(pendingId, retryId)));
            }
        }

        assertDueIndexUsed(schema, "PENDING", "scheduled_at", "idx_reminder_due_pending");
        assertDueIndexUsed(schema, "FAILED", "next_attempt_at", "idx_reminder_due_retry");
        assertStaleProcessingIndexUsed(schema, "REMINDER");
        assertStaleProcessingIndexUsed(schema, "ASSIGNMENT_CUTOFF");
    }

    @Test
    void appliesTheBaseSchemaAsTheOnlyMigration() throws Exception {
        String schema = newSchema();
        Flyway flyway = flyway(schema);
        flyway.migrate();

        assertEquals(1, Arrays.stream(flyway.info().applied())
                .filter(migration -> !migration.getType().isSynthetic())
                .count());
        assertEquals(BASE_SCHEMA_VERSION, flyway.info().current().getVersion().getVersion());
        assertEquals("base schema", flyway.info().current().getDescription());

        UUID sessionId = insertSession(schema, OWNER_ID);
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status, series_id, series_occurrence_date FROM learning_session WHERE id = ?")) {
            statement.setObject(1, sessionId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("PLANNED", result.getString("status"));
                assertNull(result.getObject("series_id"));
                assertNull(result.getObject("series_occurrence_date"));
            }
        }
        assertTrue(indexExists(schema, "idx_content_history_owner_completed"));
        assertTrue(indexExists(schema, "idx_reminder_stale_processing_by_kind"));
        assertTrue(indexExists(schema, "idx_learning_session_series_status_start"));
    }

    private static Flyway flyway(String schema) {
        return Flyway.configure()
            .dataSource(PostgresTestDatabase.jdbcUrl(), PostgresTestDatabase.username(),
                PostgresTestDatabase.password())
                .schemas(schema)
                .defaultSchema(schema)
            .locations("classpath:db/migration")
            .load();
    }

    private static UUID insertSeries(
            String schema,
            LocalDate startDate,
            LocalDate endDate,
            int duration,
            String weekdays,
            Integer reminder,
            Integer cutoff
    ) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO session_series (
                         id, owner_id, start_date, end_date, start_time, duration_minutes, timezone,
                         days_of_week, reminder_minutes_before, assignment_cutoff_minutes_before
                     ) VALUES (?, ?, ?, ?, TIME '08:00', ?, 'Europe/Madrid', ?::SMALLINT[], ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, OWNER_ID);
            statement.setObject(3, startDate);
            statement.setObject(4, endDate);
            statement.setInt(5, duration);
            statement.setString(6, weekdays);
            statement.setObject(7, reminder);
            statement.setObject(8, cutoff);
            statement.executeUpdate();
        }
        return id;
    }

    private static void insertOccurrence(String schema, UUID seriesId, LocalDate occurrenceDate) throws SQLException {
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO learning_session (
                         owner_id, starts_at, ends_at, timezone, reminder_minutes_before,
                         assignment_cutoff_minutes_before, series_id, series_occurrence_date
                     ) VALUES (?, TIMESTAMPTZ '2026-01-05 08:00:00Z', TIMESTAMPTZ '2026-01-05 09:00:00Z',
                               'Europe/Madrid', 10, 30, ?, ?)
                     """)) {
            statement.setObject(1, OWNER_ID);
            statement.setObject(2, seriesId);
            statement.setObject(3, occurrenceDate);
            statement.executeUpdate();
        }
    }

    private static UUID insertSession(String schema, UUID ownerId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO learning_session (
                         id, owner_id, starts_at, ends_at, timezone, reminder_minutes_before,
                         assignment_cutoff_minutes_before
                     ) VALUES (?, ?, TIMESTAMPTZ '2026-01-05 08:00:00Z', TIMESTAMPTZ '2026-01-05 09:00:00Z',
                               'Etc/UTC', 10, 30)
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, ownerId);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertContent(
            String schema,
            UUID ownerId,
            String url,
            String normalizedUrl,
            String status,
            Integer estimatedDurationMinutes
    ) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO content_item (
                         id, owner_id, url, normalized_url, title, content_type, status,
                         estimated_duration_minutes
                     ) VALUES (?, ?, ?, ?, 'Constraint test', 'ARTICLE', ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, ownerId);
            statement.setString(3, url);
            statement.setString(4, normalizedUrl);
            statement.setString(5, status);
            statement.setObject(6, estimatedDurationMinutes);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertSessionWithContent(
            String schema,
            UUID ownerId,
            UUID contentId,
            String startsAt,
            String endsAt
    ) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO learning_session (
                         id, owner_id, starts_at, ends_at, timezone, content_item_id,
                         reminder_minutes_before, assignment_cutoff_minutes_before
                     ) VALUES (?, ?, ?::timestamptz, ?::timestamptz, 'Etc/UTC', ?, 10, 30)
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, ownerId);
            statement.setString(3, startsAt);
            statement.setString(4, endsAt);
            statement.setObject(5, contentId);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertReminder(
            String schema,
            UUID ownerId,
            UUID sessionId,
            String kind,
            String status,
            String scheduledAt,
            String nextAttemptAt,
            int attemptCount,
            String idempotencyKey
    ) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO reminder (
                         id, owner_id, session_id, kind, status, scheduled_at, next_attempt_at,
                         attempt_count, idempotency_key
                     ) VALUES (?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, ownerId);
            statement.setObject(3, sessionId);
            statement.setString(4, kind);
            statement.setString(5, status);
            statement.setString(6, scheduledAt);
            statement.setString(7, nextAttemptAt);
            statement.setInt(8, attemptCount);
            statement.setString(9, idempotencyKey);
            statement.executeUpdate();
        }
        return id;
    }

    private static void assertDueIndexUsed(String schema, String status, String dueColumn, String indexName)
            throws SQLException {
        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
            try (ResultSet result = statement.executeQuery("EXPLAIN SELECT id FROM reminder WHERE status = '"
                    + status + "' AND " + dueColumn + " <= TIMESTAMPTZ '2026-01-05 08:15:00Z'")) {
                StringBuilder plan = new StringBuilder();
                while (result.next()) plan.append(result.getString(1));
                assertTrue(plan.toString().contains(indexName), plan.toString());
            }
        }
    }

    private static void assertStaleProcessingIndexUsed(String schema, String kind) throws SQLException {
        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
            try (ResultSet result = statement.executeQuery("EXPLAIN SELECT id FROM reminder WHERE kind = '"
                    + kind + "' AND status = 'PROCESSING' AND processing_started_at <= "
                    + "TIMESTAMPTZ '2026-01-05 08:15:00Z' ORDER BY processing_started_at, id "
                    + "LIMIT 50 FOR UPDATE SKIP LOCKED")) {
                StringBuilder plan = new StringBuilder();
                while (result.next()) plan.append(result.getString(1));
                assertTrue(plan.toString().contains("idx_reminder_stale_processing_by_kind"), plan.toString());
            }
        }
    }

    private static void assertHistoryIndexUsed(String schema) throws SQLException {
        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
                try (ResultSet result = statement.executeQuery("EXPLAIN SELECT id FROM content_item WHERE owner_id = "
                    + "'00000000-0000-0000-0000-000000000001'::uuid AND status = 'COMPLETED' "
                    + "ORDER BY completed_at DESC, id DESC LIMIT 50")) {
                StringBuilder plan = new StringBuilder();
                while (result.next()) plan.append(result.getString(1));
                assertTrue(plan.toString().contains("idx_content_history_owner_completed"), plan.toString());
            }
        }
    }

    private static void assertCheckViolation(SqlAction action) {
        SQLException exception = assertThrows(SQLException.class, action::run);
        assertEquals("23514", exception.getSQLState());
    }

    private static void assertUniqueViolation(SqlAction action) {
        SQLException exception = assertThrows(SQLException.class, action::run);
        assertEquals("23505", exception.getSQLState());
    }

    private static void assertForeignKeyViolation(SqlAction action) {
        SQLException exception = assertThrows(SQLException.class, action::run);
        assertEquals("23503", exception.getSQLState());
    }

    private static Connection connection(String schema) throws SQLException {
        return PostgresTestDatabase.connection(schema);
    }

    private String newSchema() {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        testSchemas.add(schema);
        return schema;
    }

    private static boolean indexExists(String schema, String indexName) throws SQLException {
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = ? AND indexname = ?)")) {
            statement.setString(1, schema);
            statement.setString(2, indexName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}