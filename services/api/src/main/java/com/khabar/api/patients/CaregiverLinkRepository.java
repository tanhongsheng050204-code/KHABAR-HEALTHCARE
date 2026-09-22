package com.khabar.api.patients;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CaregiverLinkRepository extends JpaRepository<CaregiverLink, UUID> {

    boolean existsByPatientIdAndCaregiverIdAndRevokedAtIsNull(UUID patientId, UUID caregiverId);

    java.util.List<CaregiverLink> findByPatientIdAndRevokedAtIsNull(UUID patientId);
}
