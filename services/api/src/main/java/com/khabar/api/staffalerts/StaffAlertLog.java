package com.khabar.api.staffalerts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Every staff-chat message Khabar sent or would have sent, with its exact text (which never names a patient)
 * and what happened to it. Rows are only ever added.
 */
@Entity
@Table(name = "staff_alert_log", indexes = @Index(name = "ix_staff_alert_log_clinic", columnList = "clinic_id"))
public class StaffAlertLog {

    /** ALERT, BRIEFING or TEST. */
    @Column(nullable = false, length = 20)
    private String kind;

    /** SENT, NOT_SENT (no staff chat linked, or Telegram not configured) or FAILED. */
    @Column(nullable = false, length = 20)
    private String outcome;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false)
    private Instant at;

    protected StaffAlertLog() {
    }

    public StaffAlertLog(UUID clinicId, String kind, String text, String outcome, String detail, Instant at) {
        this.clinicId = clinicId;
        this.kind = kind;
        this.text = text;
        this.outcome = outcome;
        this.detail = detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500);
        this.at = at;
    }

    public String getKind() { return kind; }
    public String getOutcome() { return outcome; }
    public String getText() { return text; }
    public String getDetail() { return detail; }
    public Instant getAt() { return at; }
}
