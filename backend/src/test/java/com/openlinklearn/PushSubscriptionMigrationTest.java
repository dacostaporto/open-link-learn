package com.openlinklearn;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushSubscriptionMigrationTest {

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
    void createsOwnerScopedSubscriptionsWithUniqueEndpointAndLookupIndex() throws Exception {
        String schema = newSchema();
        Flyway flyway = Flyway.configure()
            .dataSource(PostgresTestDatabase.jdbcUrl(), PostgresTestDatabase.username(),
                PostgresTestDatabase.password())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        assertEquals(BASE_SCHEMA_VERSION, flyway.info().current().getVersion().getVersion());

        UUID subscriptionId = UUID.randomUUID();
        String endpoint = "https://push.example/subscription/private-token";
        insertSubscription(schema, subscriptionId, OWNER_ID, endpoint, "original-key", "original-auth");
        assertThrows(SQLException.class, () -> insertSubscription(
                schema, UUID.randomUUID(), OWNER_ID, endpoint, "duplicate-key", "duplicate-auth"));
        assertThrows(SQLException.class, () -> insertSubscription(
                schema, UUID.randomUUID(), UUID.randomUUID(), "https://push.example/other", "key", "auth"));

        Instant refreshedAt = Instant.parse("2026-10-23T14:00:00Z");
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO push_subscription (
                         id, owner_id, endpoint, p256dh_key, auth_key, user_agent,
                         created_at, last_seen_at, active
                     ) VALUES (?, ?, ?, ?, ?, 'new browser', ?, ?, TRUE)
                     ON CONFLICT (endpoint) DO UPDATE SET
                         p256dh_key = EXCLUDED.p256dh_key,
                         auth_key = EXCLUDED.auth_key,
                         user_agent = EXCLUDED.user_agent,
                         last_seen_at = EXCLUDED.last_seen_at,
                         active = TRUE
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, OWNER_ID);
            statement.setString(3, endpoint);
            statement.setString(4, "refreshed-key");
            statement.setString(5, "refreshed-auth");
            statement.setTimestamp(6, Timestamp.from(refreshedAt));
            statement.setTimestamp(7, Timestamp.from(refreshedAt));
            statement.executeUpdate();
        }

        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, p256dh_key, auth_key, user_agent, last_seen_at, active FROM push_subscription WHERE endpoint = ?")) {
            statement.setString(1, endpoint);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(subscriptionId, result.getObject("id", UUID.class));
                assertEquals("refreshed-key", result.getString("p256dh_key"));
                assertEquals("refreshed-auth", result.getString("auth_key"));
                assertEquals("new browser", result.getString("user_agent"));
                assertEquals(refreshedAt, result.getTimestamp("last_seen_at").toInstant());
                assertEquals(true, result.getBoolean("active"));
            }
        }

        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = ?")) {
            statement.setString(1, schema);
            statement.setString(2, "idx_push_subscription_owner_active_last_seen");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertTrue(result.getString(1).contains("owner_id, active, last_seen_at"));
            }
        }
    }

    private static void insertSubscription(
            String schema,
            UUID id,
            UUID ownerId,
            String endpoint,
            String p256dhKey,
            String authKey
    ) throws SQLException {
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO push_subscription (id, owner_id, endpoint, p256dh_key, auth_key)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setObject(2, ownerId);
            statement.setString(3, endpoint);
            statement.setString(4, p256dhKey);
            statement.setString(5, authKey);
            statement.executeUpdate();
        }
    }

    private static Connection connection(String schema) throws SQLException {
        return PostgresTestDatabase.connection(schema);
    }

    private String newSchema() {
        String schema = "push_migration_" + UUID.randomUUID().toString().replace("-", "");
        testSchemas.add(schema);
        return schema;
    }
}