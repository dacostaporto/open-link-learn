package com.openlinklearn.push.api;

import com.openlinklearn.common.ApiProblem;
import com.openlinklearn.push.application.PushSubscriptionService;
import com.openlinklearn.push.application.dto.PushSubscriptionRequest;
import com.openlinklearn.push.application.dto.PushSubscriptionResponse;
import com.openlinklearn.push.application.dto.VapidPublicKeyResponse;
import com.openlinklearn.push.infrastructure.VapidProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Push", description = "Local owner Web Push subscriptions")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final VapidProperties vapidProperties;

    public PushSubscriptionController(
            PushSubscriptionService pushSubscriptionService,
            VapidProperties vapidProperties
    ) {
        this.pushSubscriptionService = pushSubscriptionService;
        this.vapidProperties = vapidProperties;
    }

    @GetMapping("/push/vapid-public-key")
    @Operation(summary = "Get the VAPID public key")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "VAPID public key returned"),
            @ApiResponse(responseCode = "503", description = "Push is not configured", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public VapidPublicKeyResponse getVapidPublicKey() {
        if (vapidProperties.vapidPublicKey() == null || vapidProperties.vapidPublicKey().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Web Push is not configured.");
        }
        return new VapidPublicKeyResponse(vapidProperties.vapidPublicKey());
    }

    @PostMapping("/push-subscriptions")
    @Operation(summary = "Create or refresh a push subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subscription saved", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PushSubscriptionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid subscription", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Endpoint belongs to another owner", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<PushSubscriptionResponse> subscribe(@RequestBody PushSubscriptionRequest request) {
        PushSubscriptionResponse response = pushSubscriptionService.subscribe(request);
        return ResponseEntity.created(URI.create("/api/v1/push-subscriptions/" + response.id())).body(response);
    }

    @DeleteMapping("/push-subscriptions/{id}")
    @Operation(summary = "Remove a push subscription")
    @ApiResponse(responseCode = "204", description = "Subscription removed")
    public ResponseEntity<Void> unsubscribe(@PathVariable UUID id) {
        pushSubscriptionService.unsubscribe(id);
        return ResponseEntity.noContent().build();
    }
}