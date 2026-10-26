package com.openlinklearn.session;

import com.openlinklearn.PostgresTestDatabase;
import com.openlinklearn.session.domain.SessionSeriesCancelledEvent;
import com.openlinklearn.session.domain.SessionSeriesScheduleChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class SessionSeriesApiIntegrationTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SCHEMA = "series_api_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @DynamicPropertySource
    static void configureIsolatedDatabase(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry, SCHEMA);
    }

    @BeforeEach
    void clearSeriesAndSetProfileDefaults() {
        jdbcTemplate.update("DELETE FROM reminder");
        jdbcTemplate.update("DELETE FROM learning_session");
        jdbcTemplate.update("DELETE FROM session_series");
        jdbcTemplate.update("""
                UPDATE app_profile
                SET default_reminder_minutes_before = 17,
                    default_assignment_cutoff_minutes_before = 55,
                    recurrence_horizon_days = 7
                WHERE id = ?
                """, OWNER_ID);
    }

    @Test
    void rejectsEmptyWeekdays() throws Exception {
        mockMvc.perform(post("/api/v1/session-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("[]", "\"Europe/Madrid\"", "\"2026-10-26\"", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Select at least one unique weekday."));
    }

    @Test
    void rejectsInvalidTimezone() throws Exception {
        mockMvc.perform(post("/api/v1/session-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("[\"MONDAY\"]", "\"Not/AZone\"", "\"2026-10-26\"", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Timezone must be a valid IANA timezone."));
    }

    @Test
    void rejectsEndDateBeforeStartDate() throws Exception {
        mockMvc.perform(post("/api/v1/session-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("[\"MONDAY\"]", "\"Europe/Madrid\"", "\"2026-10-26\"", "\"2026-10-25\"")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The series end date cannot be before its start date."));
    }

            @Test
            void readsTheSavedSeriesScheduleForEditing() throws Exception {
            LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
            LocalDate endDate = today.plusDays(14);
            String created = mockMvc.perform(post("/api/v1/session-series")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("[\"MONDAY\",\"WEDNESDAY\"]", "\"Europe/Madrid\"",
                        "\"" + today + "\"", "\"" + endDate + "\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
            UUID seriesId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created).get("id").asText());

            mockMvc.perform(get("/api/v1/session-series/{id}", seriesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seriesId.toString()))
                .andExpect(jsonPath("$.daysOfWeek").isArray())
                .andExpect(jsonPath("$.daysOfWeek[0]").value("MONDAY"))
                .andExpect(jsonPath("$.daysOfWeek[1]").value("WEDNESDAY"))
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.endDate").value(endDate.toString()))
                .andExpect(jsonPath("$.active").value(true));
            }

    @Test
    void persistsSeriesDefaultsAndReturnsGeneratedOccurrencesThroughInclusiveEndDate() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
        LocalDate endDate = today.plusDays(2);
        String response = mockMvc.perform(post("/api/v1/session-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(
                                "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"]",
                                "\"Europe/Madrid\"",
                                "\"" + today + "\"",
                                "\"" + endDate + "\"")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.endDate").value(endDate.toString()))
                .andExpect(jsonPath("$.reminderMinutesBefore").value(17))
                .andExpect(jsonPath("$.assignmentCutoffMinutesBefore").value(55))
                .andExpect(jsonPath("$.generatedThrough").value(endDate.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID seriesId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText());
        assertEquals(OWNER_ID, jdbcTemplate.queryForObject(
                "SELECT owner_id FROM session_series WHERE id = ?", UUID.class, seriesId));
        assertEquals(17, jdbcTemplate.queryForObject(
                "SELECT reminder_minutes_before FROM session_series WHERE id = ?", Integer.class, seriesId));
        assertEquals(55, jdbcTemplate.queryForObject(
                "SELECT assignment_cutoff_minutes_before FROM session_series WHERE id = ?", Integer.class, seriesId));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM learning_session WHERE series_id = ?", Long.class, seriesId));

        Instant expectedStartsAt = LocalTime.of(7, 30).atDate(today).atZone(ZoneId.of("Europe/Madrid")).toInstant();
        Instant actualStartsAt = jdbcTemplate.queryForObject("""
                        SELECT starts_at FROM learning_session
                        WHERE series_id = ? AND series_occurrence_date = ?
                        """,
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), seriesId, today);
        assertEquals(expectedStartsAt, actualStartsAt);
            Instant expectedReminderAt = expectedStartsAt.minusSeconds(17 * 60L);
            assertEquals(expectedReminderAt, pendingReminderJobTime(occurrenceId(seriesId, today)));

            jdbcTemplate.update("UPDATE app_profile SET default_reminder_minutes_before = 23 WHERE id = ?", OWNER_ID);
            assertEquals(17, reminderMinutes(seriesId, today));
            assertEquals(expectedReminderAt, pendingReminderJobTime(occurrenceId(seriesId, today)));

            UUID seriesWithNewDefault = createSeries(today, today,
                "[\"" + today.getDayOfWeek() + "\"]");
            Instant nextDefaultReminderAt = startsAt(seriesWithNewDefault, today).minusSeconds(23 * 60L);
            assertEquals(23, reminderMinutes(seriesWithNewDefault, today));
            assertEquals(nextDefaultReminderAt, pendingReminderJobTime(occurrenceId(seriesWithNewDefault, today)));
    }

        @Test
        void persistsExplicitReminderAndAssignmentCutoffOverrides() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
        String request = requestJson(
            "[\"MONDAY\"]",
            "\"Europe/Madrid\"",
            "\"" + today + "\"",
            "null",
            "5",
            "75");

        String response = mockMvc.perform(post("/api/v1/session-series")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reminderMinutesBefore").value(5))
            .andExpect(jsonPath("$.assignmentCutoffMinutesBefore").value(75))
            .andReturn()
            .getResponse()
            .getContentAsString();

        UUID seriesId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
            .readTree(response).get("id").asText());
        assertEquals(5, jdbcTemplate.queryForObject(
            "SELECT reminder_minutes_before FROM session_series WHERE id = ?", Integer.class, seriesId));
        assertEquals(75, jdbcTemplate.queryForObject(
            "SELECT assignment_cutoff_minutes_before FROM session_series WHERE id = ?", Integer.class, seriesId));
        }

        @Test
        void updatesEntireSeriesAndPreservesCompletedOccurrenceIdentityAndHistory() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
        LocalDate endDate = today.plusDays(5);
        String originalWeekdays = weekdayJson(today, today.plusDays(1), today.plusDays(2));
        UUID seriesId = createSeries(today, endDate, originalWeekdays);
        LocalDate retainedDate = today.plusDays(1);
        UUID retainedOccurrenceId = occurrenceId(seriesId, retainedDate);
        Instant completedAt = Instant.parse("2026-10-20T10:00:00Z");
        jdbcTemplate.update("""
            UPDATE learning_session
            SET status = 'COMPLETED', completed_at = ?
            WHERE series_id = ? AND series_occurrence_date = ?
            """, java.sql.Timestamp.from(completedAt), seriesId, today);

        String updateRequest = """
            {"applyTo":"ENTIRE_SERIES","startDate":"%s","endDate":"%s",
             "startTime":"08:45:00","durationMinutes":45,"timezone":"Europe/Madrid",
                 "daysOfWeek":["%s","%s"],
             "reminderMinutesBefore":6,"assignmentCutoffMinutesBefore":80}
                """.formatted(today, endDate, today.plusDays(1).getDayOfWeek(), today.plusDays(3).getDayOfWeek());

        mockMvc.perform(patch("/api/v1/session-series/{id}", seriesId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.startTime").value("08:45:00"))
            .andExpect(jsonPath("$.reminderMinutesBefore").value(6))
            .andExpect(jsonPath("$.assignmentCutoffMinutesBefore").value(80));

        assertEquals("COMPLETED", sessionStatus(seriesId, today));
        assertEquals(completedAt, completedAt(seriesId, today));
        assertEquals(LocalTime.of(7, 30).atDate(today).atZone(ZoneId.of("Europe/Madrid")).toInstant(),
            startsAt(seriesId, today));
        assertEquals(retainedOccurrenceId, occurrenceId(seriesId, retainedDate));
        assertEquals("CANCELLED", sessionStatus(seriesId, today.plusDays(2)));
        assertEquals("PLANNED", sessionStatus(seriesId, today.plusDays(3)));
        assertEquals(4, occurrenceCount(seriesId));

        LocalDate futureDate = retainedDate;
        Instant expectedStart = LocalTime.of(8, 45).atDate(futureDate)
            .atZone(ZoneId.of("Europe/Madrid")).toInstant();
        assertEquals(expectedStart, startsAt(seriesId, futureDate));
        assertEquals(expectedStart.plusSeconds(45 * 60L), endsAt(seriesId, futureDate));
        assertEquals(6, reminderMinutes(seriesId, futureDate));
        assertEquals(80, assignmentCutoffMinutes(seriesId, futureDate));
        assertEquals(expectedStart.minusSeconds(80 * 60L), pendingCutoffJobTime(retainedOccurrenceId));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT count(*) FROM reminder
            WHERE session_id = ? AND kind = 'ASSIGNMENT_CUTOFF' AND status = 'CANCELLED'
            """, Long.class, retainedOccurrenceId));
        assertEquals(expectedStart.minusSeconds(6 * 60L), pendingReminderJobTime(retainedOccurrenceId));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT count(*) FROM reminder
            WHERE session_id = ? AND kind = 'REMINDER' AND status = 'CANCELLED'
            """, Long.class, retainedOccurrenceId));
        assertEquals(1, applicationEvents.stream(SessionSeriesScheduleChangedEvent.class).count());
        }

        @Test
        void rejectsUnsupportedApplyToModeWithSupportedModeInProblemDetail() throws Exception {
        mockMvc.perform(patch("/api/v1/session-series/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applyTo":"THIS_AND_FUTURE","startDate":"2026-10-26",
                     "startTime":"07:30:00","durationMinutes":30,"timezone":"Europe/Madrid",
                     "daysOfWeek":["MONDAY"]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(
                "Unsupported applyTo mode 'THIS_AND_FUTURE'. Supported mode: ENTIRE_SERIES."));
        }

        @Test
        void preservesValidFutureOccurrencesBeyondTheCurrentGenerationHorizon() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
        LocalDate endDate = today.plusDays(10);
        jdbcTemplate.update("UPDATE app_profile SET recurrence_horizon_days = 14 WHERE id = ?", OWNER_ID);
        UUID seriesId = createDailySeries(today, endDate);
        jdbcTemplate.update("UPDATE app_profile SET recurrence_horizon_days = 7 WHERE id = ?", OWNER_ID);

        String updateRequest = """
            {"applyTo":"ENTIRE_SERIES","startDate":"%s","endDate":"%s",
             "startTime":"08:15:00","durationMinutes":30,"timezone":"Europe/Madrid",
             "daysOfWeek":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]}
            """.formatted(today, endDate);
        mockMvc.perform(patch("/api/v1/session-series/{id}", seriesId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
            .andExpect(status().isOk());

        LocalDate beyondHorizon = today.plusDays(10);
        assertEquals("PLANNED", sessionStatus(seriesId, beyondHorizon));
        assertEquals(LocalTime.of(8, 15).atDate(beyondHorizon)
            .atZone(ZoneId.of("Europe/Madrid")).toInstant(), startsAt(seriesId, beyondHorizon));
        assertEquals(11, occurrenceCount(seriesId));
        }

        @Test
        void cancelsFuturePlannedOccurrencesButPreservesCompletedHistoryAndPublishesHook() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
        UUID seriesId = createDailySeries(today, today.plusDays(3));
        Instant completedAt = Instant.parse("2026-10-20T10:00:00Z");
        jdbcTemplate.update("""
            UPDATE learning_session
            SET status = 'COMPLETED', completed_at = ?
            WHERE series_id = ? AND series_occurrence_date = ?
            """, java.sql.Timestamp.from(completedAt), seriesId, today);

        mockMvc.perform(post("/api/v1/session-series/{id}/cancel", seriesId))
            .andExpect(status().isNoContent());

        assertEquals(false, jdbcTemplate.queryForObject(
            "SELECT active FROM session_series WHERE id = ?", Boolean.class, seriesId));
        assertEquals("COMPLETED", sessionStatus(seriesId, today));
        assertEquals(completedAt, completedAt(seriesId, today));
        assertEquals(3, countOccurrencesWithStatus(seriesId, "CANCELLED"));
        assertEquals(0, jdbcTemplate.queryForObject("""
            SELECT count(*) FROM reminder job
            JOIN learning_session occurrence ON occurrence.id = job.session_id
            WHERE occurrence.series_id = ? AND job.kind = 'ASSIGNMENT_CUTOFF' AND job.status = 'PENDING'
            """, Long.class, seriesId));
        assertEquals(0, jdbcTemplate.queryForObject("""
            SELECT count(*) FROM reminder job
            JOIN learning_session occurrence ON occurrence.id = job.session_id
            WHERE occurrence.series_id = ? AND job.kind = 'REMINDER' AND job.status = 'PENDING'
            """, Long.class, seriesId));
        assertEquals(3, jdbcTemplate.queryForObject("""
            SELECT count(*) FROM reminder job
            JOIN learning_session occurrence ON occurrence.id = job.session_id
            WHERE occurrence.series_id = ? AND job.kind = 'REMINDER' AND job.status = 'CANCELLED'
            """, Long.class, seriesId));
        assertEquals(1, applicationEvents.stream(SessionSeriesCancelledEvent.class).count());

        mockMvc.perform(post("/api/v1/session-series/{id}/cancel", seriesId))
            .andExpect(status().isNoContent());
        assertEquals(1, applicationEvents.stream(SessionSeriesCancelledEvent.class).count());
        }

        private UUID createDailySeries(LocalDate startDate, LocalDate endDate) throws Exception {
            return createSeries(startDate, endDate,
                "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"]");
            }

            private UUID createSeries(LocalDate startDate, LocalDate endDate, String weekdays) throws Exception {
        String response = mockMvc.perform(post("/api/v1/session-series")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(
                        weekdays,
                    "\"Europe/Madrid\"",
                    "\"" + startDate + "\"",
                    "\"" + endDate + "\"")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
            .readTree(response).get("id").asText());
        }

    private String weekdayJson(LocalDate... dates) {
        return java.util.Arrays.stream(dates)
                .map(date -> "\"" + date.getDayOfWeek() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private UUID occurrenceId(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM learning_session WHERE series_id = ? AND series_occurrence_date = ?
                """, UUID.class, seriesId, occurrenceDate);
    }

        private String sessionStatus(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
            SELECT status FROM learning_session WHERE series_id = ? AND series_occurrence_date = ?
            """, String.class, seriesId, occurrenceDate);
        }

        private Instant completedAt(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
            SELECT completed_at FROM learning_session WHERE series_id = ? AND series_occurrence_date = ?
            """, (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), seriesId, occurrenceDate);
        }

        private Instant startsAt(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
            SELECT starts_at FROM learning_session WHERE series_id = ? AND series_occurrence_date = ?
            """, (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), seriesId, occurrenceDate);
        }

        private Instant endsAt(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
            SELECT ends_at FROM learning_session WHERE series_id = ? AND series_occurrence_date = ?
            """, (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), seriesId, occurrenceDate);
        }

        private int reminderMinutes(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
            SELECT reminder_minutes_before FROM learning_session WHERE series_id = ? AND series_occurrence_date = ?
            """, Integer.class, seriesId, occurrenceDate);
        }

        private int assignmentCutoffMinutes(UUID seriesId, LocalDate occurrenceDate) {
        return jdbcTemplate.queryForObject("""
            SELECT assignment_cutoff_minutes_before FROM learning_session
            WHERE series_id = ? AND series_occurrence_date = ?
            """, Integer.class, seriesId, occurrenceDate);
        }

        private Instant pendingCutoffJobTime(UUID sessionId) {
        return jdbcTemplate.queryForObject("""
            SELECT scheduled_at FROM reminder
            WHERE session_id = ? AND kind = 'ASSIGNMENT_CUTOFF' AND status = 'PENDING'
            """, (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), sessionId);
        }

        private Instant pendingReminderJobTime(UUID sessionId) {
        return jdbcTemplate.queryForObject("""
            SELECT scheduled_at FROM reminder
            WHERE session_id = ? AND kind = 'REMINDER' AND status = 'PENDING'
            """, (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), sessionId);
        }

        private long occurrenceCount(UUID seriesId) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM learning_session WHERE series_id = ?", Long.class, seriesId);
        }

        private long countOccurrencesWithStatus(UUID seriesId, String status) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM learning_session WHERE series_id = ? AND status = ?",
            Long.class,
            seriesId,
            status);
        }

    private static String requestJson(String weekdays, String timezone, String startDate, String endDate) {
        return requestJson(weekdays, timezone, startDate, endDate, "null", "null");
        }

        private static String requestJson(
            String weekdays,
            String timezone,
            String startDate,
            String endDate,
            String reminder,
            String assignmentCutoff
        ) {
        return """
                {"startDate":%s,"endDate":%s,"startTime":"07:30:00","durationMinutes":30,
             "timezone":%s,"daysOfWeek":%s,"reminderMinutesBefore":%s,
             "assignmentCutoffMinutesBefore":%s}
            """.formatted(startDate, endDate, timezone, weekdays, reminder, assignmentCutoff);
    }

}