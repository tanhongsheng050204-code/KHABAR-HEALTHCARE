package com.khabar.api.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByClinicIdAndStatusAndStartsAtBetweenOrderByStartsAt(UUID clinicId, Appointment.Status status, Instant from, Instant to);

    Optional<Appointment> findFirstByPatientIdAndStatusAndStartsAtAfterOrderByStartsAt(UUID patientId, Appointment.Status status, Instant after);

    boolean existsBySlotKey(String slotKey);
}
