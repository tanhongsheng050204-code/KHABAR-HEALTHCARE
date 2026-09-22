package com.khabar.api.encounters;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A result of the safety checks on one visit report. A CRITICAL one blocks finalising until overridden. */
@Entity
@Table(name = "safety_finding")
public class SafetyFinding {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Encounter encounter;

    /** allergy, interaction, duplicate, herb, dose, pregnancy, grounding, completeness, unrecognised */
    @Column(name = "check_name", nullable = false)
    private String checkName;

    /** CRITICAL or WARN */
    @Column(nullable = false)
    private String severity;

    @Column(nullable = false, length = 1000)
    private String detail;

    @Column(length = 1000)
    private String overrideReason;

    private UUID overriddenBy;

    private Instant overriddenAt;

    protected SafetyFinding() {
    }

    SafetyFinding(Encounter encounter, String checkName, String severity, String detail) {
        this.id = UUID.randomUUID();
        this.encounter = encounter;
        this.checkName = checkName;
        this.severity = severity;
        this.detail = detail;
    }

    void override(String reason, UUID by, Instant when) {
        this.overrideReason = reason;
        this.overriddenBy = by;
        this.overriddenAt = when;
    }

    public boolean isOpenCritical() {
        return "CRITICAL".equals(severity) && overrideReason == null;
    }

    public UUID getId() {
        return id;
    }

    public String getCheckName() {
        return checkName;
    }

    public String getSeverity() {
        return severity;
    }

    public String getDetail() {
        return detail;
    }

    public String getOverrideReason() {
        return overrideReason;
    }
}
