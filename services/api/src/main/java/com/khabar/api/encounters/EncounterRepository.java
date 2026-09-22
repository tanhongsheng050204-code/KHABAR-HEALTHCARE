package com.khabar.api.encounters;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    Optional<Encounter> findFirstByPatientIdAndStatusOrderByFinalisedAtDesc(UUID patientId, Encounter.Status status);
}
