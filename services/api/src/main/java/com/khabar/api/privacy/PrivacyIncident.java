package com.khabar.api.privacy;

import com.khabar.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A personal-data breach, worked through the same steps every time: contained, assessed, the Personal Data
 * Protection Commissioner notified, affected people notified when significant harm is likely, closed. Each step
 * records when it was done and by whom.
 */
@Entity
@Table(name = "privacy_incident", indexes = @Index(name = "ix_privacy_incident_clinic", columnList = "clinic_id"))
public class PrivacyIncident {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(nullable = false, length = 200)
    private String title;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "what_enc", length = 8192)
    private String whatHappened;

    private Integer peopleAffected;

    @Column(nullable = false)
    private Instant discoveredAt;

    @Column(nullable = false)
    private UUID reportedBy;

    private Instant containedAt;
    private Instant assessedAt;
    private Instant commissionerNotifiedAt;
    private Instant peopleNotifiedAt;
    private Instant closedAt;

    /** True once the assessment found significant harm likely, which makes notifying the people affected a step. */
    private boolean significantHarm;

    @Column(length = 2000)
    private String log;

    protected PrivacyIncident() {
    }

    public PrivacyIncident(UUID clinicId, String title, String whatHappened, Integer peopleAffected, Instant discoveredAt, UUID reportedBy) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.title = title;
        this.whatHappened = whatHappened;
        this.peopleAffected = peopleAffected;
        this.discoveredAt = discoveredAt;
        this.reportedBy = reportedBy;
    }

    /** Marks a step done. Returns false if it was already done. */
    boolean complete(String step, String by, Instant at, Boolean significantHarm) {
        boolean changed = switch (step) {
            case "CONTAINED" -> containedAt == null && (containedAt = at) != null;
            case "ASSESSED" -> {
                if (assessedAt != null) yield false;
                assessedAt = at;
                this.significantHarm = Boolean.TRUE.equals(significantHarm);
                yield true;
            }
            case "COMMISSIONER_NOTIFIED" -> commissionerNotifiedAt == null && (commissionerNotifiedAt = at) != null;
            case "PEOPLE_NOTIFIED" -> peopleNotifiedAt == null && (peopleNotifiedAt = at) != null;
            case "CLOSED" -> closedAt == null && (closedAt = at) != null;
            default -> throw new IllegalArgumentException("Unknown step " + step);
        };
        if (changed) {
            String line = at + " " + step + " by " + by;
            log = log == null ? line : (log + "\n" + line);
            if (log.length() > 2000) {
                log = log.substring(log.length() - 2000);
            }
        }
        return changed;
    }

    public UUID getId() { return id; }
    public UUID getClinicId() { return clinicId; }
    public String getTitle() { return title; }
    public String getWhatHappened() { return whatHappened; }
    public Integer getPeopleAffected() { return peopleAffected; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public Instant getContainedAt() { return containedAt; }
    public Instant getAssessedAt() { return assessedAt; }
    public Instant getCommissionerNotifiedAt() { return commissionerNotifiedAt; }
    public Instant getPeopleNotifiedAt() { return peopleNotifiedAt; }
    public Instant getClosedAt() { return closedAt; }
    public boolean isSignificantHarm() { return significantHarm; }
    public String getLog() { return log; }
}
