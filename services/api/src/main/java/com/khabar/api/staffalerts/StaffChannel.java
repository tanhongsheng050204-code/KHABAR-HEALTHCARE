package com.khabar.api.staffalerts;

import com.khabar.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A clinic's staff chat: the Telegram group (or private chat) that gets staff alerts and the morning briefing.
 * Messages to it carry counts and a link only, never a patient's name or words. Alerts are off until a clinic
 * doctor or administrator switches them on.
 */
@Entity
@Table(name = "staff_channel")
public class StaffChannel {

    @Id
    private UUID clinicId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "chat_enc", length = 512)
    private String chatId;

    private Instant linkedAt;

    private UUID linkedBy;

    @Column(nullable = false)
    private boolean alertsOn;

    /** Local hour (0-23) for the morning briefing; null for none. */
    private Integer briefingHour;

    @Column(length = 64)
    private String linkCodeHash;

    private Instant linkCodeExpiresAt;

    private UUID linkCodeBy;

    /** What the last alert said, as counts, so the same news is not sent twice. */
    @Column(length = 100)
    private String lastSignature;

    private Instant lastAlertAt;

    private int lastUrgent;

    private LocalDate lastBriefingDate;

    protected StaffChannel() {
    }

    public StaffChannel(UUID clinicId) {
        this.clinicId = clinicId;
    }

    void newLinkCode(String hash, Instant expiresAt, UUID by) {
        this.linkCodeHash = hash;
        this.linkCodeExpiresAt = expiresAt;
        this.linkCodeBy = by;
    }

    void link(String chatId, Instant at) {
        this.chatId = chatId;
        this.linkedAt = at;
        this.linkedBy = linkCodeBy;
        this.linkCodeHash = null;
        this.linkCodeExpiresAt = null;
        this.linkCodeBy = null;
    }

    void unlink() {
        this.chatId = null;
        this.linkedAt = null;
        this.linkedBy = null;
    }

    void configure(boolean alertsOn, Integer briefingHour) {
        this.alertsOn = alertsOn;
        this.briefingHour = briefingHour;
        if (!alertsOn) {
            this.lastSignature = null;
        }
    }

    void alerted(String signature, int urgent, Instant at) {
        this.lastSignature = signature;
        this.lastUrgent = urgent;
        this.lastAlertAt = at;
    }

    void cleared() {
        this.lastSignature = null;
        this.lastUrgent = 0;
    }

    void briefed(LocalDate day) {
        this.lastBriefingDate = day;
    }

    public UUID getClinicId() { return clinicId; }
    public String getChatId() { return chatId; }
    public Instant getLinkedAt() { return linkedAt; }
    public boolean isAlertsOn() { return alertsOn; }
    public Integer getBriefingHour() { return briefingHour; }
    public String getLinkCodeHash() { return linkCodeHash; }
    public Instant getLinkCodeExpiresAt() { return linkCodeExpiresAt; }
    public String getLastSignature() { return lastSignature; }
    public Instant getLastAlertAt() { return lastAlertAt; }
    public int getLastUrgent() { return lastUrgent; }
    public LocalDate getLastBriefingDate() { return lastBriefingDate; }
}
