package com.openlinklearn.profile.application;

import com.openlinklearn.profile.application.dto.SettingsResponse;
import com.openlinklearn.profile.application.dto.UpdateSettingsRequest;
import com.openlinklearn.profile.domain.ProfileSettings;
import com.openlinklearn.profile.domain.ProfileSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    private final ProfileSettingsRepository repository = mock(ProfileSettingsRepository.class);
    private final SettingsService service = new SettingsService(repository);

    @Test
    void persistsOnlyUpdatedProfileDefaults() {
        ProfileSettings profile = mock(ProfileSettings.class);
        when(repository.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(profile));
        when(repository.save(profile)).thenReturn(profile);
        when(profile.getTimezone()).thenReturn("Europe/Paris");
        when(profile.getDefaultReminderMinutesBefore()).thenReturn(20);
        when(profile.getDefaultAssignmentCutoffMinutesBefore()).thenReturn(45);
        when(profile.getRecurrenceHorizonDays()).thenReturn(120);

        SettingsResponse response = service.updateSettings(new UpdateSettingsRequest(" Europe/Paris ", 20, 45, 120));

        assertEquals(new SettingsResponse("Europe/Paris", 20, 45, 120), response);
        verify(profile).updateSettings("Europe/Paris", 20, 45, 120);
        verify(repository).findById(org.mockito.ArgumentMatchers.any());
        verify(repository).save(profile);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsNonIanaTimezoneWithoutChangingProfile() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateSettings(new UpdateSettingsRequest("+02:00", 10, 30, 90)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsValuesOutsideDatabaseBoundsWithoutChangingProfile() {
        assertInvalid(new UpdateSettingsRequest("Etc/UTC", -1, 30, 90));
        assertInvalid(new UpdateSettingsRequest("Etc/UTC", 1441, 30, 90));
        assertInvalid(new UpdateSettingsRequest("Etc/UTC", 10, -1, 90));
        assertInvalid(new UpdateSettingsRequest("Etc/UTC", 10, 10081, 90));
        assertInvalid(new UpdateSettingsRequest("Etc/UTC", 10, 30, 6));
        assertInvalid(new UpdateSettingsRequest("Etc/UTC", 10, 30, 366));
        assertInvalid(new UpdateSettingsRequest(null, 10, 30, 90));
        assertInvalid(new UpdateSettingsRequest("Etc/UTC", null, 30, 90));
        assertInvalid(new UpdateSettingsRequest("Etc/UTC", 10, null, 90));
        assertInvalid(new UpdateSettingsRequest("Etc/UTC", 10, 30, null));
    }

    private void assertInvalid(UpdateSettingsRequest request) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateSettings(request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository);
    }
}