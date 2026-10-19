package com.khabar.api.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A doctor-approved diet tip for one food group, in four languages. A meal photo only ever gets these words back:
 * the AI names the food groups, the clinic's own tips say what to do about them.
 */
@Entity
@Table(name = "diet_tip", indexes = @Index(name = "ix_diet_tip_clinic", columnList = "clinic_id"))
public class DietTip {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    /** One of MealAdvice.CATEGORIES. */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(name = "text_ms", nullable = false, length = 1000)
    private String textMs;

    @Column(name = "text_en", nullable = false, length = 1000)
    private String textEn;

    @Column(name = "text_zh", length = 1000)
    private String textZh;

    @Column(name = "text_ta", length = 1000)
    private String textTa;

    @Column(nullable = false)
    private UUID approvedBy;

    @Column(nullable = false)
    private Instant approvedAt;

    private Instant retiredAt;

    protected DietTip() {
    }

    public DietTip(UUID clinicId, String category, String ms, String en, String zh, String ta, UUID approvedBy, Instant approvedAt) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.category = category;
        this.textMs = ms;
        this.textEn = en;
        this.textZh = zh;
        this.textTa = ta;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    void retire(Instant at) {
        this.retiredAt = at;
    }

    /** The tip in this language, or English when it has not been written in it. */
    public String textFor(String language) {
        String text = switch (language == null ? "en" : language) {
            case "ms" -> textMs;
            case "zh" -> textZh;
            case "ta" -> textTa;
            default -> textEn;
        };
        return text == null || text.isBlank() ? textEn : text;
    }

    public UUID getId() { return id; }
    public UUID getClinicId() { return clinicId; }
    public String getCategory() { return category; }
    public String getTextMs() { return textMs; }
    public String getTextEn() { return textEn; }
    public String getTextZh() { return textZh; }
    public String getTextTa() { return textTa; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getRetiredAt() { return retiredAt; }
}
