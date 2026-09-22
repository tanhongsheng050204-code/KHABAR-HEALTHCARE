package com.khabar.api.medications;

import com.khabar.api.crypto.EncryptedStringConverter;
import com.khabar.api.identity.Role;
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

/**
 * Something the patient takes that this clinic did not prescribe: medicines from another clinic or
 * a pharmacy, supplements, jamu, traditional medicine. The safety check reads this list.
 * Stopping an item keeps the row as a record.
 */
@Entity
@Table(name = "medication_item")
public class MedicationItem {

    public enum Kind { MEDICINE, HERB }

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Patient patient;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "name_enc", nullable = false, length = 1024)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "source_enc", length = 1024)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role addedByRole;

    @Column(nullable = false)
    private UUID addedBy;

    @Column(nullable = false)
    private Instant addedAt;

    private Instant stoppedAt;

    protected MedicationItem() {
    }

    public MedicationItem(Patient patient, String name, Kind kind, String source, Role addedByRole, UUID addedBy, Instant addedAt) {
        this.id = UUID.randomUUID();
        this.patient = patient;
        this.name = name;
        this.kind = kind;
        this.source = source;
        this.addedByRole = addedByRole;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }

    public void stop(Instant when) {
        this.stoppedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public String getSource() {
        return source;
    }

    public Role getAddedByRole() {
        return addedByRole;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
