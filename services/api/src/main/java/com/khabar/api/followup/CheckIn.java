package com.khabar.api.followup;

import com.khabar.api.patients.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One scheduled "Apa khabar?" message in a patient's 30-day follow-up. */
@Entity
@Table(name = "check_in")
public class CheckIn {

    public enum Status { PENDING, SENT, FAILED }

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Patient patient;

    /** The visit that started this follow-up. */
    private UUID encounterId;

    /** Days after the visit: 1, 3, 7, 14 or 30. */
    @Column(nullable = false)
    private int dayNumber;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** Ask about shakiness before berbuka instead of the usual question. */
    private boolean fasting;

    private Instant sentAt;

    protected CheckIn() {
    }

    public CheckIn(Patient patient, UUID encounterId, LocalDate visitDay, int dayNumber, boolean fasting) {
        this.id = UUID.randomUUID();
        this.patient = patient;
        this.encounterId = encounterId;
        this.dayNumber = dayNumber;
        this.dueDate = visitDay.plusDays(dayNumber);
        this.status = Status.PENDING;
        this.fasting = fasting;
    }

    public void markSent(Instant when) {
        this.status = Status.SENT;
        this.sentAt = when;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isFasting() {
        return fasting;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
