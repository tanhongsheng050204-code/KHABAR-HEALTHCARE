package com.khabar.api.scheduling;

import com.khabar.api.crypto.EncryptedStringConverter;
import com.khabar.api.identity.Clinic;
import com.khabar.api.patients.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A booked visit. Cancelling keeps the row as a record and frees the slot. */
@Entity
@Table(name = "appointment")
public class Appointment {

    public enum Status { BOOKED, CANCELLED }

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Clinic clinic;

    @ManyToOne(optional = false)
    private Patient patient;

    @Column(nullable = false)
    private Instant startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.BOOKED;

    /** What the patient wants to be seen for, in their own words. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "reason_enc", length = 1024)
    private String reason;

    @Column(nullable = false)
    private UUID bookedBy;

    @Column(nullable = false)
    private Instant bookedAt;

    private Instant cancelledAt;

    /**
     * clinic + start time while booked, empty once cancelled. Unique, so the database itself refuses
     * a double booking even when two patients press "book" at the same moment.
     */
    @Column(unique = true, length = 100)
    private String slotKey;

    protected Appointment() {
    }

    public Appointment(Clinic clinic, Patient patient, Instant startsAt, String reason, UUID bookedBy, Instant bookedAt) {
        this.id = UUID.randomUUID();
        this.clinic = clinic;
        this.patient = patient;
        this.startsAt = startsAt;
        this.reason = reason;
        this.bookedBy = bookedBy;
        this.bookedAt = bookedAt;
        this.slotKey = slotKey(clinic.getId(), startsAt);
    }

    static String slotKey(UUID clinicId, Instant startsAt) {
        return clinicId + "|" + startsAt;
    }

    public void cancel(Instant when) {
        this.status = Status.CANCELLED;
        this.cancelledAt = when;
        this.slotKey = null;
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public Patient getPatient() {
        return patient;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
