package com.khabar.api.reminders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, UUID> {

    boolean existsByKindAndRef(String kind, UUID ref);

    java.util.List<ReminderLog> findByPatientIdAndKindOrderBySentAtDesc(UUID patientId, String kind);
}
