package com.khabar.api.encounters;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CheckInPlanner;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.intake.IntakeRecords;
import com.khabar.api.medications.MedicationList;
import com.khabar.api.messaging.Messenger;
import com.khabar.api.messaging.PatientMessages;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.Redactor;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos;
import com.khabar.api.service.AgentDtos.DraftedReport;
import com.khabar.api.service.AgentDtos.SafetyCheckResult;
import com.khabar.api.service.AgentDtos.SummaryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The doctor's visit: notes in, structured report out, safety check, overrides with a written
 * reason, then finalise, which starts the patient's 30-day follow-up and builds their summary.
 */
@RestController
public class EncounterController {

    private static final Logger log = LoggerFactory.getLogger(EncounterController.class);
    private static final int MIN_REASON_LENGTH = 10;

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final EncounterRepository encounters;
    private final VisitSummaryRepository summaries;
    private final AgentClientService agents;
    private final CheckInPlanner checkIns;
    private final AuditLog auditLog;
    private final AdjustableClock clock;
    private final PatientMessages messages;
    private final MedicationList medications;
    private final IntakeRecords intakes;

    public EncounterController(CurrentUser currentUser, PatientRepository patients, EncounterRepository encounters,
                               VisitSummaryRepository summaries, AgentClientService agents, CheckInPlanner checkIns,
                               AuditLog auditLog, AdjustableClock clock, PatientMessages messages,
                               MedicationList medications, IntakeRecords intakes) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.encounters = encounters;
        this.summaries = summaries;
        this.agents = agents;
        this.checkIns = checkIns;
        this.auditLog = auditLog;
        this.clock = clock;
        this.messages = messages;
        this.medications = medications;
        this.intakes = intakes;
    }

    public record NotesRequest(String notes, Boolean fasting) {
    }

    public record OverrideRequest(String reason) {
    }

    public record LineView(String raw, String name, Double strengthMg, Double unitsPerDose, Integer timesPerDay, String timing, boolean asNeeded) {
    }

    public record FindingView(UUID id, String check, String severity, String detail, String overrideReason) {
    }

    public record EncounterView(UUID id, UUID patientId, Encounter.Status status, String diagnosis, String plan, String followUp,
                                Double followUpWeeks, boolean fasting, List<LineView> prescription, List<FindingView> findings,
                                int openCriticalFindings, boolean checked) {
    }

    @PostMapping("/api/patients/{patientId}/encounters")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public EncounterView start(@PathVariable UUID patientId, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireDoctor(jwt);
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireSameClinic(doctor, patient);
        return view(encounters.save(new Encounter(patient, doctor, clock.instant())));
    }

    @GetMapping("/api/encounters/{id}")
    @Transactional(readOnly = true)
    public EncounterView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return view(load(id, requireDoctor(jwt)));
    }

    @PutMapping("/api/encounters/{id}/notes")
    @Transactional
    public EncounterView writeNotes(@PathVariable UUID id, @RequestBody NotesRequest request, @AuthenticationPrincipal Jwt jwt) {
        Encounter encounter = load(id, requireDoctor(jwt));
        String notes = request.notes() == null ? "" : request.notes();
        DraftedReport draft;
        try {
            draft = agents.draftReport(Redactor.redact(notes, encounter.getPatient()));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The report agent is not reachable. Your notes were not saved; try again.");
        }
        return guarded(() -> encounter.applyDraft(notes, draft, Boolean.TRUE.equals(request.fasting())), encounter);
    }

    @PostMapping("/api/encounters/{id}/check")
    @Transactional
    public EncounterView check(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Encounter encounter = load(id, requireDoctor(jwt));
        SafetyCheckResult result;
        try {
            result = agents.checkSafety(safetyDraft(encounter));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The safety check is not reachable, so the report cannot be finalised yet.");
        }
        return guarded(() -> encounter.recordFindings(result.findings() == null ? List.of() : result.findings()), encounter);
    }

    @PostMapping("/api/encounters/{id}/findings/{findingId}/override")
    @Transactional
    public EncounterView override(@PathVariable UUID id, @PathVariable UUID findingId, @RequestBody OverrideRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireDoctor(jwt);
        Encounter encounter = load(id, doctor);
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() < MIN_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a reason of at least " + MIN_REASON_LENGTH + " characters.");
        }
        if (encounter.finding(findingId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such finding on this report.");
        }
        EncounterView view = guarded(() -> encounter.override(findingId, reason, doctor.getId(), clock.instant()), encounter);
        auditLog.record(doctor, encounter.getPatient().getId(), AuditAction.OVERRODE_SAFETY_FINDING);
        return view;
    }

    @PostMapping("/api/encounters/{id}/finalise")
    @Transactional
    public EncounterView finalise(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Encounter encounter = load(id, requireDoctor(jwt));
        guarded(() -> encounter.finalise(clock.instant()), encounter);

        Patient patient = encounter.getPatient();
        checkIns.startFollowUp(patient, encounter.getId(), LocalDate.now(clock), encounter.isFasting());
        storeSummary(encounter, patient);
        return view(encounter);
    }

    /** The summary is a convenience for the patient: if it cannot be built now, the visit still finalises. */
    private void storeSummary(Encounter encounter, Patient patient) {
        try {
            List<AgentDtos.DraftedRx> rx = encounter.getPrescription().stream().map(PrescriptionLine::toDto).toList();
            SummaryResult result = agents.buildSummary(rx, patient.getPreferredLanguage(), encounter.getFollowUpWeeks(), encounter.isFasting());
            if (result != null && result.text() != null) {
                String needsDoctor = result.needsDoctor() == null ? null : String.join(", ", result.needsDoctor());
                summaries.save(new VisitSummary(patient, encounter.getId(), result.language(), result.text(), needsDoctor, clock.instant()));
                messages.send(patient, result.text(), Messenger.Kind.SUMMARY);
            }
        } catch (RuntimeException e) {
            log.warn("Summary not built for encounter {}: {}", encounter.getId(), e.getMessage());
        }
    }

    private AgentDtos.SafetyDraft safetyDraft(Encounter encounter) {
        Patient patient = encounter.getPatient();
        List<AgentDtos.CheckedRx> rx = encounter.getPrescription().stream()
                .map(line -> new AgentDtos.CheckedRx(line.getName(), line.doseMg(), line.getTimesPerDay()))
                .toList();
        Map<String, String> report = new LinkedHashMap<>();
        report.put("diagnosis", nullToEmpty(encounter.getDiagnosis()));
        report.put("plan", nullToEmpty(encounter.getPlan()));
        report.put("follow_up", nullToEmpty(encounter.getFollowUp()));
        List<AgentDtos.CurrentMed> currentMeds = medications.currentMeds(patient.getId());
        List<String> herbs = medications.herbs(patient.getId());
        List<String> allergies = new ArrayList<>(patient.allergyList());
        for (String told : intakes.reportedAllergies(patient.getId())) {
            if (allergies.stream().noneMatch(known -> known.equalsIgnoreCase(told))) {
                allergies.add(told);
            }
        }
        return new AgentDtos.SafetyDraft(new AgentDtos.PatientFacts(allergies, patient.isPregnant()), rx,
                currentMeds, herbs, report, Redactor.redact(nullToEmpty(encounter.getNotes()), patient));
    }

    private EncounterView guarded(Runnable change, Encounter encounter) {
        try {
            change.run();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return view(encounter);
    }

    private Encounter load(UUID id, AppUser doctor) {
        Encounter encounter = encounters.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireSameClinic(doctor, encounter.getPatient());
        return encounter;
    }

    private AppUser requireDoctor(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.DOCTOR || user.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Visits are written by clinic doctors.");
        }
        return user;
    }

    private static void requireSameClinic(AppUser doctor, Patient patient) {
        if (!patient.getClinic().getId().equals(doctor.getClinic().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static EncounterView view(Encounter e) {
        return new EncounterView(e.getId(), e.getPatient().getId(), e.getStatus(), e.getDiagnosis(), e.getPlan(), e.getFollowUp(),
                e.getFollowUpWeeks(), e.isFasting(),
                e.getPrescription().stream().map(l -> new LineView(l.getRaw(), l.getName(), l.getStrengthMg(), l.getUnitsPerDose(),
                        l.getTimesPerDay(), l.getTiming(), l.isAsNeeded())).toList(),
                e.getFindings().stream().map(f -> new FindingView(f.getId(), f.getCheckName(), f.getSeverity(), f.getDetail(),
                        f.getOverrideReason())).toList(),
                e.getOpenCriticalFindings(), e.isChecked());
    }
}
