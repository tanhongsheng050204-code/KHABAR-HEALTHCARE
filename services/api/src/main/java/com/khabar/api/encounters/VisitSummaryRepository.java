package com.khabar.api.encounters;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VisitSummaryRepository extends JpaRepository<VisitSummary, UUID> {

    Optional<VisitSummary> findFirstByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
