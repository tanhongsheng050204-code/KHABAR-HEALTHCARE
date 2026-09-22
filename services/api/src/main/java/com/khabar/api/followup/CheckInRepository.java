package com.khabar.api.followup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    List<CheckIn> findByPatientIdOrderByDueDate(UUID patientId);

    List<CheckIn> findByStatusAndDueDateLessThanEqual(CheckIn.Status status, LocalDate date);

    List<CheckIn> findByPatientIdAndStatus(UUID patientId, CheckIn.Status status);

    List<CheckIn> findByPatientClinicIdAndStatusAndSentAtBefore(UUID clinicId, CheckIn.Status status, java.time.Instant cutoff);
}
