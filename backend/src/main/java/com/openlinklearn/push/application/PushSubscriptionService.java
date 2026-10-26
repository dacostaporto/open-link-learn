package com.openlinklearn.push.application;

import com.openlinklearn.push.application.dto.PushSubscriptionRequest;
import com.openlinklearn.push.application.dto.PushSubscriptionResponse;
import com.openlinklearn.push.domain.PushSubscription;
import com.openlinklearn.push.domain.PushSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PushSubscriptionService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Pattern BASE64_URL = Pattern.compile("[A-Za-z0-9_-]+");

    private final PushSubscriptionRepository pushSubscriptionRepository;

    public PushSubscriptionService(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    @Transactional
    public PushSubscriptionResponse subscribe(PushSubscriptionRequest request) {
        validate(request);
        Instant now = Instant.now();
        int affected = pushSubscriptionRepository.upsertByEndpoint(
                UUID.randomUUID(),
                LOCAL_PROFILE_ID,
                request.endpoint(),
                request.keys().p256dh(),
                request.keys().auth(),
                normalizeUserAgent(request.userAgent()),
                now
        );
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription endpoint is already owned.");
        }

        PushSubscription subscription = pushSubscriptionRepository
                .findByOwnerIdAndEndpoint(LOCAL_PROFILE_ID, request.endpoint())
                .orElseThrow(() -> new IllegalStateException("The push subscription could not be loaded after saving."));
        return toResponse(subscription);
    }

    @Transactional
    public void unsubscribe(UUID id) {
        if (id != null) {
            pushSubscriptionRepository.deleteByIdAndOwnerId(id, LOCAL_PROFILE_ID);
        }
    }

    @Transactional
    public void deactivateExpired(UUID id) {
        pushSubscriptionRepository.deactivateById(id);
    }

    private void validate(PushSubscriptionRequest request) {
        if (request == null) {
            throw badRequest("A push subscription is required.");
        }
        validateEndpoint(request.endpoint());
        if (request.keys() == null) {
            throw badRequest("Both subscription keys are required.");
        }
        validateKey(request.keys().p256dh(), 65, true);
        validateKey(request.keys().auth(), 16, false);
        if (request.userAgent() != null && request.userAgent().length() > 1024) {
            throw badRequest("User agent must not exceed 1024 characters.");
        }
    }

    private void validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 2048) {
            throw badRequest("A valid HTTPS subscription endpoint is required.");
        }
        try {
            URI uri = new URI(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null) {
                throw badRequest("A valid HTTPS subscription endpoint is required.");
            }
        } catch (URISyntaxException exception) {
            throw badRequest("A valid HTTPS subscription endpoint is required.");
        }
    }

    private void validateKey(String value, int expectedBytes, boolean requireUncompressedPoint) {
        if (value == null || !BASE64_URL.matcher(value).matches()) {
            throw badRequest("Subscription keys must be valid base64url values.");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != expectedBytes || (requireUncompressedPoint && decoded[0] != 4)) {
                throw badRequest("Subscription keys have an invalid format.");
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("Subscription keys must be valid base64url values.");
        }
    }

    private String normalizeUserAgent(String userAgent) {
        return userAgent == null || userAgent.isBlank() ? null : userAgent.trim();
    }

    private PushSubscriptionResponse toResponse(PushSubscription subscription) {
        return new PushSubscriptionResponse(subscription.getId(), subscription.isActive(), subscription.getCreatedAt());
    }

    private ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}