package com.openlinklearn.content.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.openlinklearn.content.application.dto.HistoryItemResponse;
import com.openlinklearn.content.application.dto.HistoryPageResponse;
import com.openlinklearn.content.application.dto.ContentPageResponse;
import com.openlinklearn.content.application.dto.ContentResponse;
import com.openlinklearn.content.application.dto.CreateContentRequest;
import com.openlinklearn.content.application.dto.UpdateContentRequest;
import com.openlinklearn.content.domain.ContentItem;
import com.openlinklearn.content.domain.ContentRepository;
import com.openlinklearn.content.domain.ContentStatus;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.content.domain.ContentUrlValidator;
import com.openlinklearn.content.domain.DuplicateContentException;
import com.openlinklearn.session.domain.SessionRepository;
import com.openlinklearn.session.domain.SessionStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class ContentService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Set<SessionStatus> ACTIVE_SESSION_STATUSES = Set.of(SessionStatus.PLANNED, SessionStatus.IN_PROGRESS);

    private final ContentRepository contentRepository;
    private final ContentUrlValidator contentUrlValidator;
    private final ContentMetadataResolver contentMetadataResolver;
    private final SessionRepository sessionRepository;

    public ContentService(
            ContentRepository contentRepository,
            ContentUrlValidator contentUrlValidator,
            ContentMetadataResolver contentMetadataResolver,
            SessionRepository sessionRepository
    ) {
        this.contentRepository = contentRepository;
        this.contentUrlValidator = contentUrlValidator;
        this.contentMetadataResolver = contentMetadataResolver;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public ContentResponse createContent(CreateContentRequest request) {
        ContentUrlValidator.ValidatedUrl validatedUrl = contentUrlValidator.validateAndNormalize(request.url());
        if (contentRepository.existsByOwnerIdAndNormalizedUrl(LOCAL_PROFILE_ID, validatedUrl.normalizedUrl())) {
            throw new DuplicateContentException();
        }

        ContentType type = request.type() == null ? validatedUrl.type() : request.type();
        ContentMetadataResolver.ResolvedMetadata metadata = contentMetadataResolver.resolve(
            validatedUrl,
            request.title(),
            type
        );
        ContentItem item = new ContentItem(
                LOCAL_PROFILE_ID,
                validatedUrl.url(),
                validatedUrl.normalizedUrl(),
            metadata.title(),
            type,
            metadata.sourceName()
        );
        item.setThumbnailUrl(metadata.thumbnailUrl());
        item.setDurationSeconds(metadata.durationSeconds());
        if (metadata.durationSeconds() != null) {
            item.updateEstimatedDurationMinutes((metadata.durationSeconds() + 59) / 60);
        }

        try {
            return toResponse(contentRepository.saveAndFlush(item));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateContentException();
        }
    }

    @Transactional(readOnly = true)
    public ContentPageResponse getContent(ContentStatus status, ContentType type, String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be 1-100.");
        }

        String normalizedSearch = search == null || search.isBlank() ? "" : search.trim();
        Page<ContentResponse> results = contentRepository.searchByOwner(
                LOCAL_PROFILE_ID,
                status,
                type,
                normalizedSearch,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::toResponse);

        return new ContentPageResponse(
                results.getContent(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public HistoryPageResponse getHistory(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be 1-100.");
        }

        String normalizedSearch = search == null || search.isBlank() ? "" : search.trim();
        Page<HistoryItemResponse> results = contentRepository.searchCompletedByOwner(
                LOCAL_PROFILE_ID,
                ContentStatus.COMPLETED,
                normalizedSearch,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("completedAt"),
                        Sort.Order.desc("id")
                ))
        ).map(this::toHistoryResponse);

        return new HistoryPageResponse(
                results.getContent(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ContentResponse getContent(UUID id) {
        ContentItem item = contentRepository.findByIdAndOwnerId(id, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found."));
        return toResponse(item);
    }

    @Transactional
    public ContentResponse updateContent(UUID id, UpdateContentRequest request) {
        if (!request.hasTitle() && !request.hasEstimatedDurationMinutes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one editable field is required.");
        }
        if (request.hasTitle() && (request.getTitle() == null || request.getTitle().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty title is required.");
        }
        if (request.hasEstimatedDurationMinutes()
                && request.getEstimatedDurationMinutes() != null
                && request.getEstimatedDurationMinutes() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estimated duration must be a positive number of minutes.");
        }

        ContentItem item = contentRepository.findByIdAndOwnerId(id, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found."));
        if (item.getStatus() != ContentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending content can be edited.");
        }

        if (request.hasTitle()) {
            item.updateTitle(request.getTitle().trim());
        }
        if (request.hasEstimatedDurationMinutes()) {
            item.updateEstimatedDurationMinutes(request.getEstimatedDurationMinutes());
        }
        return toResponse(item);
    }

    @Transactional
    public ContentResponse completeContent(UUID id) {
        ContentItem item = contentRepository.findByIdAndOwnerIdForUpdate(id, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found."));
        if (item.getStatus() == ContentStatus.COMPLETED) {
            return toResponse(item);
        }
        if (sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(LOCAL_PROFILE_ID, id, ACTIVE_SESSION_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Content assigned to an active session cannot be completed directly.");
        }
        item.markCompleted(Instant.now());
        contentRepository.saveAndFlush(item);
        return toResponse(item);
    }

    @Transactional
    public void deleteContent(UUID id) {
        ContentItem item = contentRepository.findByIdAndOwnerId(id, LOCAL_PROFILE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found."));
        if (item.getStatus() != ContentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending content can be deleted.");
        }
        if (sessionRepository.existsByOwnerIdAndContentItem_IdAndStatusIn(
                LOCAL_PROFILE_ID,
                id,
            ACTIVE_SESSION_STATUSES
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Content assigned to an active session cannot be deleted.");
        }
        contentRepository.delete(item);
    }

    private ContentResponse toResponse(ContentItem item) {
        return new ContentResponse(
                item.getId(),
                item.getUrl(),
                item.getNormalizedUrl(),
                item.getTitle(),
                item.getContentType(),
                item.getThumbnailUrl(),
                item.getSourceName(),
                item.getDurationSeconds(),
                item.getEstimatedDurationMinutes(),
                item.getStatus(),
                null,
                item.getCreatedAt(),
                item.getCompletedAt()
        );
    }

    private HistoryItemResponse toHistoryResponse(ContentItem item) {
        return new HistoryItemResponse(
                item.getId(),
                item.getUrl(),
                item.getTitle(),
                item.getContentType(),
                item.getStatus(),
                item.getCompletedAt()
        );
    }
}