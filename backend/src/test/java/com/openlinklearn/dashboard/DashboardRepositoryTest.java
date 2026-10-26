package com.openlinklearn.dashboard;

import com.openlinklearn.PostgresTestDatabase;
import com.openlinklearn.content.domain.ContentItem;
import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import com.openlinklearn.session.domain.UpcomingSession;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DashboardRepositoryTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-10-25T00:30:00Z");
    private static final String SCHEMA = "dashboard_repository_"
            + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private SessionRepository sessionRepository;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @BeforeEach
        void seedOtherOwner() {
                jdbcTemplate.update("""
                                INSERT INTO app_profile (id, display_name, timezone)
                                VALUES (?, 'Other owner', 'Etc/UTC')
                                ON CONFLICT (id) DO NOTHING
                                """, OTHER_OWNER_ID);
        }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry, SCHEMA);
    }

    @Test
    void contentCountsAreOwnerAndStatusScoped() {
        ContentItem pendingOne = content(OWNER_ID, "pending-one");
        ContentItem pendingTwo = content(OWNER_ID, "pending-two");
        ContentItem completed = content(OWNER_ID, "completed");
        completed.markCompleted(NOW.minusSeconds(3600));
        ContentItem otherOwnerPending = content(OTHER_OWNER_ID, "other-owner");
        contentRepository.saveAll(List.of(pendingOne, pendingTwo, completed, otherOwnerPending));

        assertEquals(2, contentRepository.countByOwnerIdAndStatus(OWNER_ID, ContentStatus.PENDING));
        assertEquals(1, contentRepository.countByOwnerIdAndStatus(OWNER_ID, ContentStatus.COMPLETED));
        assertEquals(1, contentRepository.countByOwnerIdAndStatus(OTHER_OWNER_ID, ContentStatus.PENDING));
    }

    @Test
    void sessionQueriesUseHalfOpenDayOwnerUpcomingAndAssignmentFilters() {
        Instant dayStart = Instant.parse("2026-10-24T22:00:00Z");
        Instant dayEnd = Instant.parse("2026-10-25T23:00:00Z");
        ContentItem assigned = contentRepository.save(content(OWNER_ID, "assigned"));
        LearningSession plannedToday = session(OWNER_ID, Instant.parse("2026-10-25T09:00:00Z"), null);
        LearningSession completedToday = session(OWNER_ID, Instant.parse("2026-10-25T12:00:00Z"), null);
        completedToday.markCompleted(NOW);
        LearningSession skippedToday = session(OWNER_ID, Instant.parse("2026-10-25T13:00:00Z"), null);
        skippedToday.skipAndReleaseContent();
        LearningSession atDayEnd = session(OWNER_ID, dayEnd, null);
        LearningSession atDayStart = session(OWNER_ID, dayStart, null);
        LearningSession beforeDayStart = session(OWNER_ID, dayStart.minusSeconds(1), null);
        LearningSession anotherOwnerToday = session(OTHER_OWNER_ID, Instant.parse("2026-10-25T10:00:00Z"), null);
        LearningSession upcomingUnassigned = session(OWNER_ID, Instant.parse("2026-10-25T01:00:00Z"), null);
        LearningSession upcomingAssigned = session(OWNER_ID, Instant.parse("2026-10-25T02:00:00Z"), assigned);
        LearningSession futureCancelled = session(OWNER_ID, Instant.parse("2026-10-25T03:00:00Z"), null);
        futureCancelled.cancelAndReleaseContent();
        sessionRepository.saveAll(List.of(
                plannedToday, completedToday, skippedToday, atDayEnd, atDayStart, beforeDayStart,
                anotherOwnerToday, upcomingUnassigned, upcomingAssigned, futureCancelled));

        long todayCount = sessionRepository.countByOwnerIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndStatusIn(
                OWNER_ID, dayStart, dayEnd,
                Set.of(SessionStatus.PLANNED, SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED));
        long unassignedUpcomingCount = sessionRepository
                .countByOwnerIdAndStartsAtGreaterThanEqualAndStatusAndContentItemIsNull(
                        OWNER_ID, NOW, SessionStatus.PLANNED);
        List<UpcomingSession> nextSessions = sessionRepository.findNextUpcomingByOwner(
                OWNER_ID, SessionStatus.PLANNED, NOW, PageRequest.of(0, 1));

        assertEquals(5, todayCount);
        assertEquals(3, unassignedUpcomingCount);
        assertEquals(1, nextSessions.size());
        assertEquals(upcomingUnassigned.getId(), nextSessions.get(0).id());
        assertEquals("Unassigned session", nextSessions.get(0).title());
    }

    private ContentItem content(UUID ownerId, String key) {
        return new ContentItem(ownerId, "https://example.org/" + key, "https://example.org/" + key,
                key, ContentType.ARTICLE, "example.org");
    }

    private LearningSession session(UUID ownerId, Instant startsAt, ContentItem contentItem) {
        return new LearningSession(ownerId, startsAt, startsAt.plusSeconds(1800), "Europe/Madrid", contentItem, 10, 30);
    }
}