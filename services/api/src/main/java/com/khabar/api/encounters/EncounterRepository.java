package com.khabar.api.encounters;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Encounter e where e.id = :id")
    Optional<Encounter> lockById(@Param("id") UUID id);

    List<Encounter> findByPatientIdAndStatusOrderByFinalisedAt(UUID patientId, Encounter.Status status);

    Optional<Encounter> findFirstByPatientIdAndStatusOrderByFinalisedAtDesc(UUID patientId, Encounter.Status status);
}
