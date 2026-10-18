package com.khabar.api.followup;

import com.khabar.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A note on a follow-up case, for the clinic team: a handover, a question, an @mention. Notes are encrypted at
 * rest and only ever added. They are never sent to the patient or to the AI service.
 */
@Entity
@Table(name = "case_note", indexes = {
        @Index(name = "ix_case_note_case", columnList = "case_id"),
        @Index(name = "ix_case_note_clinic", columnList = "clinic_id")
})
public class CaseNote {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String authorName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "text_enc", nullable = false, length = 4096)
    private String text;

    /** Comma-separated user IDs of the clinic staff @mentioned in the note. */
    @Column(length = 2000)
    private String mentions;

    @Column(nullable = false)
    private Instant at;

    protected CaseNote() {
    }

    public CaseNote(UUID caseId, UUID clinicId, UUID authorId, String authorName, String text, List<UUID> mentioned, Instant at) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.clinicId = clinicId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.text = text;
        this.mentions = mentioned.isEmpty() ? null : String.join(",", mentioned.stream().map(UUID::toString).toList());
        this.at = at;
    }

    public List<UUID> mentioned() {
        if (mentions == null || mentions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(mentions.split(",")).map(UUID::fromString).toList();
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getClinicId() { return clinicId; }
    public UUID getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getText() { return text; }
    public String getMentions() { return mentions; }
    public Instant getAt() { return at; }
}
