package com.khabar.api.reminders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** A refill or appointment reminder, kept so each is sent once, with the patient's answer to it. */
@Entity
@Table(name = "reminder_log", uniqueConstraints = @UniqueConstraint(name = "ux_reminder_log_ref", columnNames = {"kind", "ref"}))
public class ReminderLog {

    /** REFILL (ref = the visit) or APPOINTMENT (ref = the appointment). */
    @Column(nullable = false, length = 20)
    private String kind;

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private UUID ref;

    @Column(nullable = false)
    private Instant sentAt;

    private boolean delivered;

    /** For appointments: COMING or CANT_COME. */
    @Column(length = 20)
    private String response;

    private Instant respondedAt;

    protected ReminderLog() {
    }

    public ReminderLog(UUID patientId, String kind, UUID ref, Instant sentAt) {
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.kind = kind;
        this.ref = ref;
        this.sentAt = sentAt;
    }

    void delivered(boolean delivered) {
        this.delivered = delivered;
    }

    boolean answer(String response, Instant at) {
        if (this.response != null) {
            return false;
        }
        this.response = response;
        this.respondedAt = at;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public String getKind() { return kind; }
    public UUID getRef() { return ref; }
    public Instant getSentAt() { return sentAt; }
    public String getResponse() { return response; }
}
