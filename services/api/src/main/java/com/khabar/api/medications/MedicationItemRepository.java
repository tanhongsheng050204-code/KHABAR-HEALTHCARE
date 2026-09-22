package com.khabar.api.medications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MedicationItemRepository extends JpaRepository<MedicationItem, UUID> {

    List<MedicationItem> findByPatientIdAndStoppedAtIsNullOrderByAddedAt(UUID patientId);
}
