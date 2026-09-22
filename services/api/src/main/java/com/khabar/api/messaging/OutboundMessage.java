package com.khabar.api.messaging;

import com.khabar.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Every message Khabar sent (or tried to send) to a patient, whatever the channel. */
@Entity
@Table(name = "outbound_message")
public class OutboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID patientId;

    /** CHECK_IN or SUMMARY */
    @Column(nullable = false)
    private String kind;

    /** whatsapp or outbox */
    @Column(nullable = false)
    private String channel;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "text_enc", nullable = false, length = 16384)
    private String text;

    private boolean delivered;

    private String providerId;

    @Column(length = 2000)
    private String error;

    @Column(nullable = false)
    private Instant createdAt;

    protected OutboundMessage() {
    }

    public OutboundMessage(UUID patientId, Messenger.Kind kind, String channel, String text, Messenger.Result result, Instant createdAt) {
        this.patientId = patientId;
        this.kind = kind.name();
        this.channel = channel;
        this.text = text;
        this.delivered = result.delivered();
        this.providerId = result.providerId();
        this.error = result.error();
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public String getKind() {
        return kind;
    }

    public String getChannel() {
        return channel;
    }

    public String getText() {
        return text;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
