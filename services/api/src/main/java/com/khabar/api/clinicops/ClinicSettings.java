package com.khabar.api.clinicops;

import com.khabar.api.followup.TriageLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * What a clinic has decided about running follow-up: its hours, whom to escalate to, and how quickly each
 * level must be acknowledged. Khabar makes no response-time promise of its own; each clinic sets and approves
 * these. A clinic without a row uses the defaults below until an administrator saves its own.
 */
@Entity
@Table(name = "clinic_settings")
public class ClinicSettings {

    public static final int DEFAULT_RED_MINUTES = 30;
    public static final int DEFAULT_WATCH_MINUTES = 240;
    public static final int DEFAULT_REVIEW_MINUTES = 1440;

    @Id
    private UUID clinicId;

    @Column(length = 200)
    private String hours;

    @Column(length = 200)
    private String escalationContact;

    @Column(nullable = false)
    private int redAckMinutes = DEFAULT_RED_MINUTES;

    @Column(nullable = false)
    private int watchAckMinutes = DEFAULT_WATCH_MINUTES;

    @Column(nullable = false)
    private int reviewAckMinutes = DEFAULT_REVIEW_MINUTES;

    private Instant updatedAt;

    private UUID updatedBy;

    protected ClinicSettings() {
    }

    public ClinicSettings(UUID clinicId) {
        this.clinicId = clinicId;
    }

    public void update(String hours, String escalationContact, int red, int watch, int review, UUID by, Instant at) {
        this.hours = hours;
        this.escalationContact = escalationContact;
        this.redAckMinutes = red;
        this.watchAckMinutes = watch;
        this.reviewAckMinutes = review;
        this.updatedBy = by;
        this.updatedAt = at;
    }

    /** Minutes a case at this level may wait before someone acknowledges it. */
    public int ackMinutes(TriageLevel level) {
        return switch (level) {
            case RED -> redAckMinutes;
            case WATCH -> watchAckMinutes;
            default -> reviewAckMinutes;
        };
    }

    public UUID getClinicId() { return clinicId; }
    public String getHours() { return hours; }
    public String getEscalationContact() { return escalationContact; }
    public int getRedAckMinutes() { return redAckMinutes; }
    public int getWatchAckMinutes() { return watchAckMinutes; }
    public int getReviewAckMinutes() { return reviewAckMinutes; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
}
