package com.openlinklearn.profile.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileSettingsRepository extends JpaRepository<ProfileSettings, UUID> {
}