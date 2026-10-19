package com.khabar.api.access;

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
 * A family Telegram group the patient chose to link: it gets copies of their medicine and visit reminders (with the
 * same Taken / Missed buttons) and the caregiver alerts. Linked by the patient with a short-lived code.
 */
@Entity
@Table(name = "family_group", indexes = {
        @Index(name = "ix_family_group_chat", columnList = "chat_index"),
        @Index(name = "ix_family_group_code", columnList = "link_code_hash")})
public class FamilyGroup {

    @Id
    private UUID patientId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "chat_enc", length = 512)
    private String chatId;

    @Column(name = "chat_index", length = 64)
    private String chatIndex;

    private Instant linkedAt;

    @Column(name = "link_code_hash", length = 64)
    private String linkCodeHash;

    private Instant linkCodeExpiresAt;

    protected FamilyGroup() {
    }

    public FamilyGroup(UUID patientId) {
        this.patientId = patientId;
    }

    void newLinkCode(String hash, Instant expiresAt) {
        this.linkCodeHash = hash;
        this.linkCodeExpiresAt = expiresAt;
    }

    void link(String chatId, String chatIndex, Instant at) {
        this.chatId = chatId;
        this.chatIndex = chatIndex;
        this.linkedAt = at;
        this.linkCodeHash = null;
        this.linkCodeExpiresAt = null;
    }

    /** Unlinking also cancels any code not yet used, so an old code cannot re-link the chat. */
    void unlink() {
        this.chatId = null;
        this.chatIndex = null;
        this.linkedAt = null;
        this.linkCodeHash = null;
        this.linkCodeExpiresAt = null;
    }

    public UUID getPatientId() { return patientId; }
    public String getChatId() { return chatId; }
    public Instant getLinkedAt() { return linkedAt; }
    public Instant getLinkCodeExpiresAt() { return linkCodeExpiresAt; }
}
