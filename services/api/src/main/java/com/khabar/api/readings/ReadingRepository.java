package com.khabar.api.readings;

import com.khabar.api.followup.TriageLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReadingRepository extends JpaRepository<Reading, UUID> {

    List<Reading> findTop30ByPatientIdOrderByMeasuredAtDesc(UUID patientId);

    List<Reading> findByPatientClinicId(UUID clinicId);

    List<Reading> findByPatientClinicIdAndHandledAtIsNullAndLevelNot(UUID clinicId, TriageLevel level);

    List<Reading> findByPatientIdAndHandledAtIsNull(UUID patientId);
}
