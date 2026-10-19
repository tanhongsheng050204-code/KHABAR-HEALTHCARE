package com.khabar.api.pathways;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatientPathwayRepository extends JpaRepository<PatientPathway, UUID> {

    List<PatientPathway> findByPatientIdAndEndedAtIsNullOrderByStartedAt(UUID patientId);

    List<PatientPathway> findByClinicIdAndEndedAtIsNull(UUID clinicId);
}
