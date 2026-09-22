package com.khabar.api.encounters;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EncounterRepository extends JpaRepository<Encounter, UUID> {
}
