package com.openlinklearn.session.application;

import com.openlinklearn.PostgresTestDatabase;
import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.profile.domain.ProfileSettingsRepository;
import com.openlinklearn.session.application.cutoff.AssignmentCutoffJobService;
import com.openlinklearn.session.application.reminder.ReminderJobService;
import com.openlinklearn.session.domain.ReminderJobRepository;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionSeriesRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionOccurrenceGenerationServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant LOCAL_MIDNIGHT_IN_MADRID = Instant.parse("2026-05-31T22:00:00Z");
    private String schema;
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private SessionOccurrenceGenerationService generationService;
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void createIsolatedDatabaseSchema() throws Exception {
        schema = "generation_" + UUID.randomUUID().toString().replace("-", "");
        PostgresTestDatabase.createSchema(schema);

        Flyway.configure()
            .dataSource(PostgresTestDatabase.jdbcUrl(), PostgresTestDatabase.username(),
                PostgresTestDatabase.password())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DataSource dataSource = new DriverManagerDataSource(
            PostgresTestDatabase.jdbcUrl(schema), PostgresTestDatabase.username(),
            PostgresTestDatabase.password());
        jdbcTemplate = new JdbcTemplate(dataSource);
        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setDataSource(dataSource);
        entityManagerFactoryBean.setPackagesToScan("com.openlinklearn");
        entityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactoryBean.setJpaPropertyMap(Map.of(
            "hibernate.hbm2ddl.auto", "none",
            "hibernate.default_schema", schema));
        entityManagerFactoryBean.afterPropertiesSet();
        entityManagerFactory = entityManagerFactoryBean.getObject();
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        var repositoryFactory = new JpaRepositoryFactory(entityManager);
        transactionTemplate = new TransactionTemplate(new JpaTransactionManager(entityManagerFactory));
        SessionRepository sessionRepository = repositoryFactory.getRepository(SessionRepository.class);
        var contentRepository = repositoryFactory.getRepository(ContentRepository.class);
        var reminderJobRepository = repositoryFactory.getRepository(ReminderJobRepository.class);
        var assignmentService = new AutomaticContentAssignmentService(sessionRepository, contentRepository);
        var assignmentCutoffJobService = new AssignmentCutoffJobService(
                sessionRepository, reminderJobRepository, assignmentService);
        var reminderJobService = new ReminderJobService(sessionRepository, reminderJobRepository);
        generationService = new SessionOccurrenceGenerationService(
            repositoryFactory.getRepository(SessionSeriesRepository.class),
            sessionRepository,
            repositoryFactory.getRepository(ProfileSettingsRepository.class),
            new WeeklyRecurrenceService(),
            assignmentCutoffJobService,
            reminderJobService);
        jdbcTemplate.update("""
                UPDATE app_profile
                SET recurrence_horizon_days = 7,
                    default_reminder_minutes_before = 12,
                    default_assignment_cutoff_minutes_before = 40
                WHERE id = ?
                """, OWNER_ID);
    }

    @AfterEach
    void dropIsolatedDatabaseSchema() {
        if (schema == null) {
            return;
        }
        entityManagerFactory.close();
        PostgresTestDatabase.dropSchema(schema);
    }

    @Test
    void repeatedGenerationIsSafePreservesCompletedOccurrencesAndIncludesHorizonBoundary() {
        UUID seriesId = insertSeries(LocalDate.parse("2026-06-01"), null, "{1,2,3,4,5,6,7}");

        SessionOccurrenceGenerationService.GenerationResult first = inTransaction(
                () -> generationService.generateThrough(LOCAL_MIDNIGHT_IN_MADRID));
        assertEquals(1, first.activeSeriesChecked());
        assertEquals(8, first.occurrencesInserted());
        assertEquals(LocalDate.parse("2026-06-08"), latestOccurrenceDate(seriesId));
        assertEquals(Instant.parse("2026-06-01T06:00:00Z"), startsAt(seriesId, LocalDate.parse("2026-06-01")));
        assertEquals(8, cutoffJobCount(seriesId));
        assertEquals(Instant.parse("2026-06-01T05:20:00Z"), cutoffJobTime(seriesId, LocalDate.parse("2026-06-01")));
        assertEquals(8, reminderJobCount(seriesId));
        assertEquals(Instant.parse("2026-06-01T05:48:00Z"), reminderJobTime(seriesId, LocalDate.parse("2026-06-01")));
        assertEquals(12, reminderMinutes(seriesId));
        assertEquals(40, assignmentCutoffMinutes(seriesId));

        jdbcTemplate.update("""
                UPDATE learning_session
                SET status = 'COMPLETED', completed_at = TIMESTAMPTZ '2026-06-02 10:00:00Z'
                WHERE series_id = ? AND series_occurrence_date = DATE '2026-06-02'
                """, seriesId);

        SessionOccurrenceGenerationService.GenerationResult repeated = inTransaction(
                () -> generationService.generateThrough(LOCAL_MIDNIGHT_IN_MADRID));

        assertEquals(0, repeated.occurrencesInserted());
        assertEquals(8, occurrenceCount(seriesId));
        assertEquals(8, cutoffJobCount(seriesId));
        assertEquals(8, reminderJobCount(seriesId));
        assertEquals("COMPLETED", status(seriesId, LocalDate.parse("2026-06-02")));
        assertNotNull(completedAt(seriesId, LocalDate.parse("2026-06-02")));

        SessionOccurrenceGenerationService.GenerationResult replenished = inTransaction(
            () -> generationService.generateThrough(Instant.parse("2026-06-04T22:00:00Z")));

        assertEquals(4, replenished.occurrencesInserted());
        assertEquals(12, occurrenceCount(seriesId));
        assertEquals(12, cutoffJobCount(seriesId));
        assertEquals(12, reminderJobCount(seriesId));
        assertEquals(LocalDate.parse("2026-06-12"), latestOccurrenceDate(seriesId));
        assertEquals("COMPLETED", status(seriesId, LocalDate.parse("2026-06-02")));
    }

    @Test
    void doesNotGenerateOccurrencesAfterTheInclusiveSeriesEndDate() {
        UUID seriesId = insertSeries(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-03"),
                "{1,2,3,4,5,6,7}");

        SessionOccurrenceGenerationService.GenerationResult result = inTransaction(
                () -> generationService.generateThrough(LOCAL_MIDNIGHT_IN_MADRID));

        assertEquals(3, result.occurrencesInserted());
        assertEquals(LocalDate.parse("2026-06-03"), latestOccurrenceDate(seriesId));
    }

    @Test
    void concurrentGenerationUsesDatabaseUniquenessToAvoidDuplicates() throws Exception {
        UUID seriesId = insertSeries(LocalDate.parse("2026-06-01"), null, "{1,2,3,4,5,6,7}");
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<SessionOccurrenceGenerationService.GenerationResult> first = executor.submit(() ->
                    inTransaction(() -> {
                        await(startTogether);
                        return generationService.generateThrough(LOCAL_MIDNIGHT_IN_MADRID);
                    }));
            Future<SessionOccurrenceGenerationService.GenerationResult> second = executor.submit(() ->
                    inTransaction(() -> {
                        await(startTogether);
                        return generationService.generateThrough(LOCAL_MIDNIGHT_IN_MADRID);
                    }));

            int inserted = first.get(15, TimeUnit.SECONDS).occurrencesInserted()
                    + second.get(15, TimeUnit.SECONDS).occurrencesInserted();
            assertEquals(8, inserted);
            assertEquals(8, occurrenceCount(seriesId));
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID insertSeries(LocalDate startDate, LocalDate endDate, String weekdays) {
        UUID seriesId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO session_series (
                            id, owner_id, start_date, end_date, start_time, duration_minutes,
                            timezone, days_of_week, active
                        ) VALUES (?, ?, ?, ?, TIME '08:00', 30, 'Europe/Madrid', ?::SMALLINT[], TRUE)
                        """,
                seriesId, OWNER_ID, startDate, endDate, weekdays);
        return seriesId;
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> action) {
        return transactionTemplate.execute(status -> {
            try {
                return action.call();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long occurrenceCount(UUID seriesId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM learning_session WHERE series_id = ?", Long.class, seriesId);
        return count == null ? 0 : count;
    }

        private long cutoffJobCount(UUID seriesId) {
                Long count = jdbcTemplate.queryForObject("""
                                SELECT count(*) FROM reminder job
                                JOIN learning_session session ON session.id = job.session_id
                                WHERE session.series_id = ? AND job.kind = 'ASSIGNMENT_CUTOFF'
                                """, Long.class, seriesId);
                return count == null ? 0 : count;
        }

        private Instant cutoffJobTime(UUID seriesId, LocalDate occurrenceDate) {
                return jdbcTemplate.queryForObject("""
                                SELECT job.scheduled_at FROM reminder job
                                JOIN learning_session session ON session.id = job.session_id
                                WHERE session.series_id = ? AND session.series_occurrence_date = ?
                                    AND job.kind = 'ASSIGNMENT_CUTOFF'
                                """, (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), seriesId, occurrenceDate);
        }

        private long reminderJobCount(UUID seriesId) {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM reminder job
                    JOIN learning_session session ON session.id = job.session_id
                    WHERE session.series_id = ? AND job.kind = 'REMINDER'
                    """, Long.class, seriesId);
            return count == null ? 0 : count;
        }

        private Instant reminderJobTime(UUID seriesId, LocalDate occurrenceDate) {
            return jdbcTemplate.queryForObject("""
                    SELECT job.scheduled_at FROM reminder job
                    JOIN learning_session session ON session.id = job.session_id
                    WHERE session.series_id = ? AND session.series_occurrence_date = ?
                        AND job.kind = 'REMINDER' AND job.status = 'PENDING'
                    """, (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), seriesId, occurrenceDate);
        }

    private LocalDate latestOccurrenceDate(UUID seriesId) {
        return jdbcTemplate.queryForObject(
                "SELECT max(series_occurrence_date) FROM learning_session WHERE series_id = ?",
                LocalDate.class,
                seriesId);
    }

    private Instant startsAt(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
                        SELECT starts_at FROM learning_session
                        WHERE series_id = ? AND series_occurrence_date = ?
                        """,
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
                seriesId,
                occurrenceDate);
    }

    private int reminderMinutes(UUID seriesId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT reminder_minutes_before FROM learning_session WHERE series_id = ? LIMIT 1",
                Integer.class,
                seriesId);
        return value == null ? -1 : value;
    }

    private int assignmentCutoffMinutes(UUID seriesId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT assignment_cutoff_minutes_before FROM learning_session WHERE series_id = ? LIMIT 1",
                Integer.class,
                seriesId);
        return value == null ? -1 : value;
    }

    private String status(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
                        SELECT status FROM learning_session
                        WHERE series_id = ? AND series_occurrence_date = ?
                        """,
                String.class,
                seriesId,
                occurrenceDate);
    }

    private Instant completedAt(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
                        SELECT completed_at FROM learning_session
                        WHERE series_id = ? AND series_occurrence_date = ?
                        """,
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
                seriesId,
                occurrenceDate);
    }

}