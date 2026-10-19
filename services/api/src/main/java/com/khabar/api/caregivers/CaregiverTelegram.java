package com.khabar.api.caregivers;

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
 * A caregiver's own Telegram chat for alerts about the patients who share alerts with them. Linked with a
 * short-lived code from Khabar, sent to the bot as "/link CODE".
 */
@Entity
@Table(name = "caregiver_telegram", indexes = {
        @Index(name = "ix_caregiver_telegram_chat", columnList = "chat_index"),
        @Index(name = "ix_caregiver_telegram_code", columnList = "link_code_hash")})
public class CaregiverTelegram {

    @Id
    private UUID caregiverId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "chat_enc", length = 512)
    private String chatId;

    @Column(name = "chat_index", length = 64)
    private String chatIndex;

    private Instant linkedAt;

    @Column(name = "link_code_hash", length = 64)
    private String linkCodeHash;

    private Instant linkCodeExpiresAt;

    protected CaregiverTelegram() {
    }

    public CaregiverTelegram(UUID caregiverId) {
        this.caregiverId = caregiverId;
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

    public UUID getCaregiverId() { return caregiverId; }
    public String getChatId() { return chatId; }
    public Instant getLinkedAt() { return linkedAt; }
    public Instant getLinkCodeExpiresAt() { return linkCodeExpiresAt; }
}
