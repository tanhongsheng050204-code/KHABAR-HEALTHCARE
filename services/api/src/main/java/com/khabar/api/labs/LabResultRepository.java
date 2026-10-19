package com.khabar.api.labs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LabResultRepository extends JpaRepository<LabResult, UUID> {

    List<LabResult> findByPatientIdOrderByTakenOnAscRecordedAtAsc(UUID patientId);
}
