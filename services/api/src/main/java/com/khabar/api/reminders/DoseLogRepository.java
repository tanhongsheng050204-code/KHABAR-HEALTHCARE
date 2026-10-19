package com.khabar.api.reminders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DoseLogRepository extends JpaRepository<DoseLog, UUID> {

    boolean existsByPatientIdAndDayAndSlot(UUID patientId, LocalDate day, String slot);

    List<DoseLog> findByPatientIdAndDayGreaterThanEqualOrderByDayAscSentAtAsc(UUID patientId, LocalDate from);

    long countByPatientIdAndResponseAndDayGreaterThanEqual(UUID patientId, String response, LocalDate from);
}
