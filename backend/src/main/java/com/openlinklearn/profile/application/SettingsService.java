package com.openlinklearn.profile.application;

import com.openlinklearn.profile.application.dto.SettingsResponse;
import com.openlinklearn.profile.application.dto.UpdateSettingsRequest;
import com.openlinklearn.profile.domain.ProfileSettings;
import com.openlinklearn.profile.domain.ProfileSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.UUID;

@Service
public class SettingsService {

    private static final UUID LOCAL_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ProfileSettingsRepository profileSettingsRepository;

    public SettingsService(ProfileSettingsRepository profileSettingsRepository) {
        this.profileSettingsRepository = profileSettingsRepository;
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return toResponse(getProfileSettings());
    }

    @Transactional
    public SettingsResponse updateSettings(UpdateSettingsRequest request) {
        if (request == null) {
            throw badRequest("All four settings fields are required.");
        }
        String timezone = request.timezone() == null ? "" : request.timezone().trim();
        if (timezone.isBlank() || timezone.length() > 64 || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw badRequest("Timezone must be a valid IANA timezone.");
        }
        if (request.defaultReminderMinutesBefore() == null
                || request.defaultReminderMinutesBefore() < 0 || request.defaultReminderMinutesBefore() > 1440) {
            throw badRequest("Default reminder lead time must be between 0 and 1440 minutes.");
        }
        if (request.defaultAssignmentCutoffMinutesBefore() == null
                || request.defaultAssignmentCutoffMinutesBefore() < 0
                || request.defaultAssignmentCutoffMinutesBefore() > 10080) {
            throw badRequest("Default assignment cutoff must be between 0 and 10080 minutes.");
        }
        if (request.recurrenceHorizonDays() == null
                || request.recurrenceHorizonDays() < 7 || request.recurrenceHorizonDays() > 365) {
            throw badRequest("Recurrence horizon must be between 7 and 365 days.");
        }

        ProfileSettings settings = getProfileSettings();
        settings.updateSettings(
                timezone,
                request.defaultReminderMinutesBefore(),
                request.defaultAssignmentCutoffMinutesBefore(),
                request.recurrenceHorizonDays()
        );
        return toResponse(profileSettingsRepository.save(settings));
    }

    private ProfileSettings getProfileSettings() {
        return profileSettingsRepository.findById(LOCAL_PROFILE_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "The local profile is missing; verify that Flyway migrations have run."));
    }

    private SettingsResponse toResponse(ProfileSettings settings) {
        return new SettingsResponse(
                settings.getTimezone(),
                settings.getDefaultReminderMinutesBefore(),
                settings.getDefaultAssignmentCutoffMinutesBefore(),
                settings.getRecurrenceHorizonDays()
        );
    }

    private ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}