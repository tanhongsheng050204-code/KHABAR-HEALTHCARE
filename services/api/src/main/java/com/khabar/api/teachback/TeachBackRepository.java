package com.khabar.api.teachback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TeachBackRepository extends JpaRepository<TeachBack, UUID> {

    List<TeachBack> findTop10ByPatientIdOrderByTakenAtDesc(UUID patientId);

    List<TeachBack> findByClinicIdAndTakenAtBetween(UUID clinicId, Instant from, Instant to);
}
