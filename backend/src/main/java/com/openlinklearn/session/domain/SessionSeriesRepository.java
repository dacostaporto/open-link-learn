package com.openlinklearn.session.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionSeriesRepository extends JpaRepository<SessionSeries, UUID> {
    List<SessionSeries> findAllByActiveTrueOrderByIdAsc();

    Optional<SessionSeries> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<SessionSeries> findByIdAndOwnerIdAndActiveTrue(UUID id, UUID ownerId);
}