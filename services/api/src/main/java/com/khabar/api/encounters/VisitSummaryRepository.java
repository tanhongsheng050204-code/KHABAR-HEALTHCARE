package com.khabar.api.encounters;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface VisitSummaryRepository extends JpaRepository<VisitSummary, UUID> {

    Optional<VisitSummary> findFirstByPatientIdOrderByCreatedAtDesc(UUID patientId);

    /** Newest first, without summaries that are still held back for the doctor. */
    @Query("select s from VisitSummary s where s.patient.id = :patientId and (s.heldReason is null or s.releasedAt is not null) order by s.createdAt desc")
    List<VisitSummary> findReleasedByPatient(@Param("patientId") UUID patientId, Pageable page);

    default Optional<VisitSummary> latestReleased(UUID patientId) {
        return findReleasedByPatient(patientId, PageRequest.of(0, 1)).stream().findFirst();
    }

    Optional<VisitSummary> findFirstByEncounterIdOrderByCreatedAtDesc(UUID encounterId);
}
