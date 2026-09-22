package com.khabar.api.encounters;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.intake.IntakeController.IntakeView;
import com.khabar.api.intake.IntakeRecords;
import com.khabar.api.medications.MedicationController.ItemView;
import com.khabar.api.medications.MedicationList;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos.FindingDto;
import com.khabar.api.service.AgentDtos.PatientFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The pre-visit page: everything the doctor should know before calling the patient in, on one screen.
 * The latest intake, the last visit, what the patient takes from everywhere (checked for duplicates,
 * clashes, herbs and allergies) and what they have said since.
 */
@RestController
public class PreVisitController {

    private static final Logger log = LoggerFactory.getLogger(PreVisitController.class);

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final EncounterRepository encounters;
    private final IntakeRecords intakes;
    private final MedicationList medications;
    private final PatientReplyRepository replies;
    private final AgentClientService agents;
    private final AuditLog auditLog;

    public PreVisitController(CurrentUser currentUser, PatientRepository patients, EncounterRepository encounters, IntakeRecords intakes,
                              MedicationList medications, PatientReplyRepository replies, AgentClientService agents, AuditLog auditLog) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.encounters = encounters;
        this.intakes = intakes;
        this.medications = medications;
        this.replies = replies;
        this.agents = agents;
        this.auditLog = auditLog;
    }

    public record PatientSummary(UUID id, String fullName, String preferredLanguage, List<String> allergies, boolean pregnant) {
    }

    public record LastVisit(UUID encounterId, Instant finalisedAt, String doctor, String diagnosis, String plan, String followUp,
                            List<String> prescription) {
    }

    public record RecentReply(Instant receivedAt, TriageLevel level, String text, boolean missedDose) {
    }

    /** reconciliation is null when the checks could not run, so the screen can say so rather than show "no problems". */
    public record PreVisit(PatientSummary patient, IntakeView intake, LastVisit lastVisit, List<ItemView> medications,
                           List<FindingDto> reconciliation, List<RecentReply> recentReplies) {
    }

    @GetMapping("/api/patients/{patientId}/previsit")
    @Transactional
    public PreVisit previsit(@PathVariable UUID patientId, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = currentUser.from(jwt);
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (doctor.getRole() != Role.DOCTOR || doctor.getClinic() == null || !doctor.getClinic().getId().equals(patient.getClinic().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The pre-visit page is for the patient's clinic.");
        }
        auditLog.record(doctor, patient.getId(), AuditAction.VIEWED_PREVISIT);

        LastVisit lastVisit = encounters.findFirstByPatientIdAndStatusOrderByFinalisedAtDesc(patient.getId(), Encounter.Status.FINAL)
                .map(e -> new LastVisit(e.getId(), e.getFinalisedAt(), e.getDoctor().getDisplayName(), e.getDiagnosis(), e.getPlan(),
                        e.getFollowUp(), e.getPrescription().stream().map(PrescriptionLine::getRaw).toList()))
                .orElse(null);
        List<RecentReply> recent = replies.findTop5ByPatientIdOrderByReceivedAtDesc(patient.getId()).stream()
                .map(r -> new RecentReply(r.getReceivedAt(), r.getLevel(), r.getText(), r.isMissedDose()))
                .toList();

        return new PreVisit(
                new PatientSummary(patient.getId(), patient.getFullName(), patient.getPreferredLanguage(), patient.allergyList(), patient.isPregnant()),
                intakes.latestView(patient.getId()).orElse(null),
                lastVisit,
                medications.active(patient.getId()).stream().map(ItemView::of).toList(),
                reconciliation(patient),
                recent);
    }

    private List<FindingDto> reconciliation(Patient patient) {
        List<String> allergies = new java.util.ArrayList<>(patient.allergyList());
        intakes.reportedAllergies(patient.getId()).stream()
                .filter(told -> allergies.stream().noneMatch(known -> known.equalsIgnoreCase(told)))
                .forEach(allergies::add);
        try {
            return agents.reconcile(new PatientFacts(allergies, patient.isPregnant()),
                    medications.currentMeds(patient.getId()), medications.herbs(patient.getId()));
        } catch (RuntimeException e) {
            log.warn("Medicine list not checked for patient {}: {}", patient.getId(), e.getMessage());
            return null;
        }
    }
}
