package com.khabar.api.reminders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReminderSettingsRepository extends JpaRepository<ReminderSettings, UUID> {

    List<ReminderSettings> findByDoseRemindersTrue();

    List<ReminderSettings> findByAppointmentRemindersTrue();
}
