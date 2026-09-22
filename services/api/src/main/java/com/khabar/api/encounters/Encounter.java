package com.khabar.api.encounters;

import com.khabar.api.crypto.EncryptedStringConverter;
import com.khabar.api.identity.AppUser;
import com.khabar.api.patients.Patient;
import com.khabar.api.service.AgentDtos.DraftedReport;
import com.khabar.api.service.AgentDtos.FindingDto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A visit and its report. A report with an unresolved CRITICAL safety finding can never be FINAL:
 * this class refuses it, the API refuses it, and the database CHECK constraint below refuses it.
 */
@Entity
@Table(name = "encounter")
@Check(constraints = "status <> 'FINAL' or open_critical_findings = 0")
public class Encounter {

    public enum Status { DRAFT, FINAL }

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Patient patient;

    @ManyToOne(optional = false)
    private AppUser doctor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    /** The doctor's own notes (typed or transcribed), stored encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "notes_enc", length = 16384)
    private String notes;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "diagnosis_enc", length = 4096)
    private String diagnosis;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "plan_enc", length = 8192)
    private String plan;

    private String followUp;

    private Double followUpWeeks;

    /** Ramadan fasting mode for the patient's reminders and summary. */
    private boolean fasting;

    @ElementCollection
    @CollectionTable(name = "encounter_prescription")
    @OrderColumn(name = "line_no")
    private List<PrescriptionLine> prescription = new ArrayList<>();

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "finding_no")
    private List<SafetyFinding> findings = new ArrayList<>();

    /** Kept in step with the findings so the database can enforce the finalising rule by itself. */
    @Column(name = "open_critical_findings", nullable = false)
    private int openCriticalFindings;

    /** Goes up every time the report content changes; the safety check must have seen the latest one. */
    private int revision;

    private Integer checkedRevision;

    private Instant startedAt;

    private Instant finalisedAt;

    protected Encounter() {
    }

    public Encounter(Patient patient, AppUser doctor, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.patient = patient;
        this.doctor = doctor;
        this.startedAt = startedAt;
    }

    void applyDraft(String notes, DraftedReport draft, boolean fasting) {
        requireDraft();
        this.notes = notes;
        this.diagnosis = draft.diagnosis();
        this.plan = draft.plan();
        this.followUp = draft.followUp();
        this.followUpWeeks = draft.followUpWeeks();
        this.fasting = fasting;
        this.prescription.clear();
        if (draft.prescription() != null) {
            draft.prescription().forEach(rx -> this.prescription.add(PrescriptionLine.from(rx)));
        }
        this.findings.clear();
        recount();
        this.revision++;
    }

    void recordFindings(List<FindingDto> results) {
        requireDraft();
        this.findings.clear();
        results.forEach(f -> this.findings.add(new SafetyFinding(this, f.check(), f.severity(), f.detail())));
        recount();
        this.checkedRevision = this.revision;
    }

    SafetyFinding override(UUID findingId, String reason, UUID doctorId, Instant when) {
        requireDraft();
        SafetyFinding finding = findings.stream().filter(f -> f.getId().equals(findingId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such finding on this report"));
        finding.override(reason, doctorId, when);
        recount();
        return finding;
    }

    void finalise(Instant when) {
        requireDraft();
        if (checkedRevision == null || checkedRevision != revision) {
            throw new IllegalStateException("Run the safety check on the latest version of the report first.");
        }
        if (openCriticalFindings > 0) {
            throw new IllegalStateException("Every CRITICAL finding needs a written reason before the report can be finalised.");
        }
        this.status = Status.FINAL;
        this.finalisedAt = when;
    }

    private void requireDraft() {
        if (status == Status.FINAL) {
            throw new IllegalStateException("This report is already final.");
        }
    }

    private void recount() {
        this.openCriticalFindings = (int) findings.stream().filter(SafetyFinding::isOpenCritical).count();
    }

    public boolean isChecked() {
        return checkedRevision != null && checkedRevision == revision;
    }

    public Optional<SafetyFinding> finding(UUID id) {
        return findings.stream().filter(f -> f.getId().equals(id)).findFirst();
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public AppUser getDoctor() {
        return doctor;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getPlan() {
        return plan;
    }

    public String getFollowUp() {
        return followUp;
    }

    public Double getFollowUpWeeks() {
        return followUpWeeks;
    }

    public boolean isFasting() {
        return fasting;
    }

    public List<PrescriptionLine> getPrescription() {
        return prescription;
    }

    public List<SafetyFinding> getFindings() {
        return findings;
    }

    public int getOpenCriticalFindings() {
        return openCriticalFindings;
    }

    public Instant getFinalisedAt() {
        return finalisedAt;
    }
}
