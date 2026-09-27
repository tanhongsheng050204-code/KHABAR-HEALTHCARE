package com.khabar.api.clinicops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.DayOfWeek;
import java.util.UUID;

/** Who owns the follow-up list on one day of the week, and who covers for them. */
@Entity
@Table(name = "rota_entry", uniqueConstraints = @UniqueConstraint(name = "uk_rota_clinic_day", columnNames = {"clinic_id", "day_of_week"}))
public class RotaEntry {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private UUID primaryUserId;

    private UUID backupUserId;

    protected RotaEntry() {
    }

    public RotaEntry(UUID clinicId, DayOfWeek dayOfWeek, UUID primaryUserId, UUID backupUserId) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.dayOfWeek = dayOfWeek;
        this.primaryUserId = primaryUserId;
        this.backupUserId = backupUserId;
    }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public UUID getPrimaryUserId() { return primaryUserId; }
    public UUID getBackupUserId() { return backupUserId; }
}
