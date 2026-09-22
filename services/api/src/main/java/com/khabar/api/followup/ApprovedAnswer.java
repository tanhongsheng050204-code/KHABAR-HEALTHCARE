package com.khabar.api.followup;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.Clinic;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An answer a doctor has written and approved for a common follow-up question. Patients only ever
 * receive these exact words; nothing is generated. Retiring an answer keeps it as a record.
 */
@Entity
@Table(name = "approved_answer")
public class ApprovedAnswer {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Clinic clinic;

    @Column(nullable = false, length = 200)
    private String title;

    /** Phrases that mean a reply is asking this question, in any of the four languages. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "approved_answer_trigger", joinColumns = @JoinColumn(name = "answer_id"))
    @OrderColumn(name = "position")
    @Column(name = "phrase", nullable = false, length = 200)
    private List<String> triggers = new ArrayList<>();

    /** The answer, by language (ms, en, zh, ta). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "approved_answer_text", joinColumns = @JoinColumn(name = "answer_id"))
    @MapKeyColumn(name = "language", length = 8)
    @Column(name = "text", nullable = false, length = 2000)
    private Map<String, String> texts = new LinkedHashMap<>();

    @ManyToOne(optional = false)
    private AppUser approvedBy;

    @Column(nullable = false)
    private Instant approvedAt;

    private Instant retiredAt;

    protected ApprovedAnswer() {
    }

    public ApprovedAnswer(Clinic clinic, String title, List<String> triggers, Map<String, String> texts, AppUser approvedBy, Instant approvedAt) {
        this.id = UUID.randomUUID();
        this.clinic = clinic;
        this.title = title;
        this.triggers = new ArrayList<>(triggers);
        this.texts = new LinkedHashMap<>(texts);
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    public void retire(Instant when) {
        this.retiredAt = when;
    }

    /** The answer in the patient's language, or English, or nothing: never a language they didn't ask for. */
    public String textFor(String language) {
        return texts.containsKey(language) ? texts.get(language) : texts.get("en");
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getTriggers() {
        return triggers;
    }

    public Map<String, String> getTexts() {
        return texts;
    }

    public AppUser getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
