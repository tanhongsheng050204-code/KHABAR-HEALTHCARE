package com.khabar.api.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrivacyIncidentRepository extends JpaRepository<PrivacyIncident, UUID> {

    List<PrivacyIncident> findByClinicIdOrderByDiscoveredAtDesc(UUID clinicId);
}
