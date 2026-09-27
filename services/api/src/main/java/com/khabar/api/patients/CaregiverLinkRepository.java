package com.khabar.api.patients;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CaregiverLinkRepository extends JpaRepository<CaregiverLink, UUID> {

    boolean existsByPatientIdAndCaregiverIdAndRevokedAtIsNull(UUID patientId, UUID caregiverId);

    boolean existsByPatientIdAndCaregiverIdAndRevokedAtIsNullAndScope(UUID patientId, UUID caregiverId, CaregiverScope scope);

    java.util.List<CaregiverLink> findByPatientIdAndRevokedAtIsNull(UUID patientId);

    java.util.List<CaregiverLink> findByCaregiverIdAndRevokedAtIsNull(UUID caregiverId);
}
