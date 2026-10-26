package com.openlinklearn.dashboard.application;

import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.dashboard.application.dto.DashboardResponse;
import com.openlinklearn.profile.application.SettingsService;
import com.openlinklearn.profile.application.dto.SettingsResponse;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ContentRepository contentRepository = mock(ContentRepository.class);
    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final SettingsService settingsService = mock(SettingsService.class);

    @Test
    void usesConfiguredLocalDayAcrossTheAutumnDstTransition() {
        Instant now = Instant.parse("2026-10-25T00:30:00Z");
        DashboardService service = serviceAt(now, "Europe/Madrid");

        service.getDashboard();

        verify(sessionRepository).countByOwnerIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndStatusIn(
                OWNER_ID,
                Instant.parse("2026-10-24T22:00:00Z"),
                Instant.parse("2026-10-25T23:00:00Z"),
                Set.of(SessionStatus.PLANNED, SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED));
    }

    @Test
    void usesA23HourLocalDayAcrossTheSpringDstTransition() {
        DashboardService service = serviceAt(Instant.parse("2026-03-29T00:30:00Z"), "Europe/Madrid");

        service.getDashboard();

        verify(sessionRepository).countByOwnerIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndStatusIn(
                OWNER_ID,
                Instant.parse("2026-03-28T23:00:00Z"),
                Instant.parse("2026-03-29T22:00:00Z"),
                Set.of(SessionStatus.PLANNED, SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED));
    }

    @Test
    void switchesToTheNewLocalDayAtTheExactMidnightBoundary() {
        Instant midnightMadrid = Instant.parse("2026-10-25T23:00:00Z");
        DashboardService service = serviceAt(midnightMadrid, "Europe/Madrid");

        service.getDashboard();

        verify(sessionRepository).countByOwnerIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndStatusIn(
                OWNER_ID,
                midnightMadrid,
                Instant.parse("2026-10-26T23:00:00Z"),
                Set.of(SessionStatus.PLANNED, SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED));
        verify(sessionRepository).countByOwnerIdAndStartsAtGreaterThanEqualAndStatusAndContentItemIsNull(
                OWNER_ID, midnightMadrid, SessionStatus.PLANNED);
    }

    @Test
    void returnsCountsAndAnEmptyNextSessionWhenNoUpcomingSessionExists() {
        Instant now = Instant.parse("2026-10-23T10:00:00Z");
        DashboardService service = serviceAt(now, "Etc/UTC");
        when(contentRepository.countByOwnerIdAndStatus(OWNER_ID, ContentStatus.PENDING)).thenReturn(4L);
        when(contentRepository.countByOwnerIdAndStatus(OWNER_ID, ContentStatus.COMPLETED)).thenReturn(7L);
        when(sessionRepository.findNextUpcomingByOwner(OWNER_ID, SessionStatus.PLANNED, now,
            PageRequest.of(0, 1))).thenReturn(List.of());

        DashboardResponse response = service.getDashboard();

        assertEquals(4, response.pendingContentCount());
        assertEquals(7, response.completedContentCount());
        assertNull(response.nextSession());
    }

    private DashboardService serviceAt(Instant now, String timezone) {
        when(settingsService.getSettings()).thenReturn(new SettingsResponse(timezone, 10, 30, 90));
        return new DashboardService(
                contentRepository,
                sessionRepository,
                settingsService,
                Clock.fixed(now, ZoneOffset.UTC));
    }
}