package com.khabar.api.reminders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppointmentOutcomeRepository extends JpaRepository<AppointmentOutcome, UUID> {

    List<AppointmentOutcome> findByClinicIdAndHandledAtIsNull(UUID clinicId);

    List<AppointmentOutcome> findByPatientIdAndHandledAtIsNull(UUID patientId);
}
