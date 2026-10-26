package com.openlinklearn;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class PostgresTestDatabase {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("openlinklearn_test")
            .withUsername("openlinklearn_test")
            .withPassword("openlinklearn_test");

    static {
        POSTGRES.start();
    }

    private PostgresTestDatabase() {
    }

    public static void configure(DynamicPropertyRegistry registry, String schema) {
        createSchema(schema);
        registry.add("spring.datasource.url", () -> jdbcUrl(schema));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.schemas", () -> schema);
        registry.add("spring.flyway.default-schema", () -> schema);
    }

    public static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    public static String jdbcUrl(String schema) {
        validateSchema(schema);
        String baseUrl = POSTGRES.getJdbcUrl();
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    public static String username() {
        return POSTGRES.getUsername();
    }

    public static String password() {
        return POSTGRES.getPassword();
    }

    public static Connection connection(String schema) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(schema), username(), password());
    }

    public static void createSchema(String schema) {
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create the PostgreSQL test schema.", exception);
        }
    }

    public static void dropSchema(String schema) {
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not drop the PostgreSQL test schema.", exception);
        }
    }

    private static void validateSchema(String schema) {
        if (schema == null || !schema.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL test schema name.");
        }
    }
}