package com.openlinklearn.session.application;

import com.openlinklearn.content.domain.ContentItem;
import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.session.domain.LearningSession;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
public class AutomaticContentAssignmentService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SessionRepository sessionRepository;
    private final ContentRepository contentRepository;

    public AutomaticContentAssignmentService(
            SessionRepository sessionRepository,
            ContentRepository contentRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.contentRepository = contentRepository;
    }

    @Transactional
    public Optional<UUID> assignPendingContent(UUID sessionId) {
        LearningSession session = sessionRepository.findByIdAndOwnerIdForUpdate(sessionId, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session was not found."));
        if (session.getStatus() != SessionStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Automatic content can only be assigned to planned sessions.");
        }
        if (session.getContentItem() != null) {
            return Optional.of(session.getContentItem().getId());
        }

        Optional<ContentItem> candidate = contentRepository.findRandomPendingUnassignedForUpdate(session.getOwnerId());
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        ContentItem content = candidate.get();
        session.updateContentItem(content);
        sessionRepository.saveAndFlush(session);
        return Optional.of(content.getId());
    }
}