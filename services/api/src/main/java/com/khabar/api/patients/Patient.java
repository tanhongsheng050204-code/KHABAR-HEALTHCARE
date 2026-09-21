package com.khabar.api.patients;

import com.khabar.api.crypto.EncryptedStringConverter;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.Clinic;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A patient's identity record. Only this service ever sees it: the AI service and
 * Neo4j know the patient only by graphId, which carries no personal information.
 */
@Entity
@Table(name = "patient")
public class Patient {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Clinic clinic;

    /** The patient's own sign-in, if they have one. */
    @ManyToOne
    private AppUser account;

    @Column(nullable = false)
    private String fullName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ic_number_enc", length = 512)
    private String icNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_enc", length = 512)
    private String phone;

    /** ms, en, zh or ta */
    @Column(nullable = false)
    private String preferredLanguage;

    /** Random id used for this patient everywhere outside this service. */
    @Column(nullable = false, unique = true)
    private UUID graphId;

    private boolean pregnant;

    /** The day the 30-day follow-up started (the visit day). Null when the patient is not in follow-up. */
    private LocalDate followUpStart;

    protected Patient() {
    }

    public void startFollowUp(LocalDate visitDay) {
        this.followUpStart = visitDay;
    }

    /** Day 1 is the day after the visit. Null when not in follow-up. */
    public Integer followUpDay(LocalDate today) {
        return followUpStart == null ? null : (int) ChronoUnit.DAYS.between(followUpStart, today) + 1;
    }

    public LocalDate getFollowUpStart() {
        return followUpStart;
    }

    public Patient(Clinic clinic, AppUser account, String fullName, String icNumber, String phone, String preferredLanguage) {
        this.id = UUID.randomUUID();
        this.graphId = UUID.randomUUID();
        this.clinic = clinic;
        this.account = account;
        this.fullName = fullName;
        this.icNumber = icNumber;
        this.phone = phone;
        this.preferredLanguage = preferredLanguage;
    }

    /** Shows only the last 4 digits, e.g. ******-**-5566. */
    public String icMasked() {
        if (icNumber == null || icNumber.length() < 4) {
            return null;
        }
        return "******-**-" + icNumber.substring(icNumber.length() - 4);
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public AppUser getAccount() {
        return account;
    }

    public String getFullName() {
        return fullName;
    }

    public String getIcNumber() {
        return icNumber;
    }

    public String getPhone() {
        return phone;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public UUID getGraphId() {
        return graphId;
    }

    public boolean isPregnant() {
        return pregnant;
    }
}
