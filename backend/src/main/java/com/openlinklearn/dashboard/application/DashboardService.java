package com.openlinklearn.dashboard.application;

import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.dashboard.application.dto.DashboardResponse;
import com.openlinklearn.dashboard.application.dto.NextSessionResponse;
import com.openlinklearn.profile.application.SettingsService;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import com.openlinklearn.session.domain.UpcomingSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DashboardService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Set<SessionStatus> TODAY_STATUSES = Set.of(SessionStatus.PLANNED, SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED);

    private final ContentRepository contentRepository;
    private final SessionRepository sessionRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    public DashboardService(ContentRepository contentRepository, SessionRepository sessionRepository, SettingsService settingsService, Clock clock) {
        this.contentRepository = contentRepository;
        this.sessionRepository = sessionRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        Instant now = clock.instant();
        ZoneId timezone = ZoneId.of(settingsService.getSettings().timezone());
        LocalDate localToday = LocalDate.ofInstant(now, timezone);
        Instant dayStart = localToday.atStartOfDay(timezone).toInstant();
        Instant dayEnd = localToday.plusDays(1).atStartOfDay(timezone).toInstant();

        List<UpcomingSession> nextSessions = sessionRepository.findNextUpcomingByOwner(
                LOCAL_PROFILE_ID, SessionStatus.PLANNED, now, PageRequest.of(0, 1));

        return new DashboardResponse(
                contentRepository.countByOwnerIdAndStatus(LOCAL_PROFILE_ID, ContentStatus.PENDING),
                contentRepository.countByOwnerIdAndStatus(LOCAL_PROFILE_ID, ContentStatus.COMPLETED),
                sessionRepository.countByOwnerIdAndStartsAtGreaterThanEqualAndStartsAtLessThanAndStatusIn(
                        LOCAL_PROFILE_ID, dayStart, dayEnd, TODAY_STATUSES),
                sessionRepository.countByOwnerIdAndStartsAtGreaterThanEqualAndStatusAndContentItemIsNull(
                        LOCAL_PROFILE_ID, now, SessionStatus.PLANNED),
                nextSessions.stream().findFirst()
                        .map(session -> new NextSessionResponse(session.id(), session.startsAt(), session.title()))
                        .orElse(null)
        );
    }
    
}