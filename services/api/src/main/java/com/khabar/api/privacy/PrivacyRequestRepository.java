package com.khabar.api.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrivacyRequestRepository extends JpaRepository<PrivacyRequest, UUID> {

    List<PrivacyRequest> findByClinicIdOrderByCreatedAtDesc(UUID clinicId);

    List<PrivacyRequest> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
