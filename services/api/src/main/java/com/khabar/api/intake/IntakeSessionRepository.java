package com.khabar.api.intake;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IntakeSessionRepository extends JpaRepository<IntakeSession, UUID> {

    Optional<IntakeSession> findFirstByPatientIdAndCompletedAtIsNullOrderByStartedAtDesc(UUID patientId);

    Optional<IntakeSession> findFirstByPatientIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(UUID patientId);
}
