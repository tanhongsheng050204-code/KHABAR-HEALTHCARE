package com.khabar.api.reminders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** What a patient chose to be reminded about on Telegram. Everything is off until the patient switches it on. */
@Entity
@Table(name = "reminder_settings")
public class ReminderSettings {

    @Id
    private UUID patientId;

    @Column(nullable = false)
    private boolean doseReminders;

    @Column(nullable = false)
    private boolean appointmentReminders;

    private Instant updatedAt;

    protected ReminderSettings() {
    }

    public ReminderSettings(UUID patientId) {
        this.patientId = patientId;
    }

    void update(boolean doses, boolean appointments, Instant at) {
        this.doseReminders = doses;
        this.appointmentReminders = appointments;
        this.updatedAt = at;
    }

    public UUID getPatientId() { return patientId; }
    public boolean isDoseReminders() { return doseReminders; }
    public boolean isAppointmentReminders() { return appointmentReminders; }
}
