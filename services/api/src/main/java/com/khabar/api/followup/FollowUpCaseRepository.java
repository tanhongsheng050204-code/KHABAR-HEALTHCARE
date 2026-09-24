package com.khabar.api.followup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpCaseRepository extends JpaRepository<FollowUpCase, UUID> {

    Optional<FollowUpCase> findByPatientIdAndClosedAtIsNull(UUID patientId);

    List<FollowUpCase> findByClinicIdAndClosedAtIsNull(UUID clinicId);
}
