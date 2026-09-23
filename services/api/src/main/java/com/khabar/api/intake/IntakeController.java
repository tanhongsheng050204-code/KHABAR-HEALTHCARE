package com.khabar.api.intake;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.graph.PatientGraphSync;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.medications.MedicationItem;
import com.khabar.api.medications.MedicationList;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientAccessPolicy;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.Redactor;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos.PreVisitReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The patient's pre-visit intake chat. The patient is identified by their sign-in, never by
 * anything in the request body, and the AI service only ever receives their graphId.
 * The chat is saved as it goes; when it is complete the doctor gets a pre-visit report.
 */
@RestController
public class IntakeController {

    private static final Logger log = LoggerFactory.getLogger(IntakeController.class);

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientAccessPolicy policy;
    private final AgentClientService agents;
    private final IntakeSessionRepository sessions;
    private final IntakeRecords records;
    private final AuditLog auditLog;
    private final AdjustableClock clock;
    private final MedicationList medications;
    private final PatientGraphSync graphSync;

    public IntakeController(CurrentUser currentUser, PatientRepository patients, PatientAccessPolicy policy, AgentClientService agents,
                            IntakeSessionRepository sessions, IntakeRecords records, AuditLog auditLog, AdjustableClock clock,
                            MedicationList medications, PatientGraphSync graphSync) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.policy = policy;
        this.agents = agents;
        this.sessions = sessions;
        this.records = records;
        this.auditLog = auditLog;
        this.clock = clock;
        this.medications = medications;
        this.graphSync = graphSync;
    }

    public record ChatMessage(String role, String content) {
    }

    public record IntakeRequest(List<ChatMessage> messages) {
    }

    public record IntakeResponse(String nextQuestion, boolean complete) {
    }

    public record IntakeView(UUID sessionId, Instant completedAt, PreVisitReport report, List<Map<String, String>> transcript) {
    }

    @PostMapping("/api/intake/chat")
    @Transactional
    public IntakeResponse chat(@RequestBody IntakeRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Intake is filled in by the patient.");
        }
        Patient patient = patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));

        List<Map<String, String>> history = request.messages() == null ? List.of() : request.messages().stream()
                .map(m -> Map.of("role", String.valueOf(m.role()), "content", Redactor.redact(String.valueOf(m.content()), patient)))
                .toList();

        Map<String, Object> reply = agents.processIntake(patient.getGraphId().toString(), patient.getPreferredLanguage(), history, known(patient));
        String nextQuestion = String.valueOf(reply.get("next_question"));
        boolean complete = Boolean.TRUE.equals(reply.get("is_complete"));

        save(patient, user, history, nextQuestion, complete);
        return new IntakeResponse(nextQuestion, complete);
    }

    @GetMapping("/api/patients/{patientId}/intake")
    @Transactional
    public IntakeView latest(@PathVariable UUID patientId, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!policy.isPatientOrTheirClinic(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        IntakeView view = records.latestView(patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No finished intake yet."));
        if (user.getRole() != Role.PATIENT) {
            auditLog.record(user, patient.getId(), AuditAction.VIEWED_INTAKE);
        }
        return view;
    }

    /** What the clinic already knows, so the chat confirms it instead of starting from nothing. No identifiers. */
    private Map<String, Object> known(Patient patient) {
        Map<String, Object> context = new java.util.LinkedHashMap<>();
        List<String> taken = medications.active(patient.getId()).stream()
                .map(MedicationItem::getName)
                .map(name -> Redactor.redact(name, patient))
                .toList();
        if (!taken.isEmpty()) {
            context.put("medicines", taken);
        }
        if (!patient.allergyList().isEmpty()) {
            context.put("allergies", patient.allergyList());
        }
        return context;
    }

    private void save(Patient patient, AppUser user, List<Map<String, String>> history, String nextQuestion, boolean complete) {
        Instant now = clock.instant();
        IntakeSession session = sessions.findFirstByPatientIdAndCompletedAtIsNullOrderByStartedAtDesc(patient.getId())
                .orElseGet(() -> new IntakeSession(patient, now));
        List<Map<String, String>> transcript = new ArrayList<>(history);
        transcript.add(Map.of("role", "assistant", "content", nextQuestion));
        session.update(records.toJson(transcript), now);

        if (complete) {
            PreVisitReport report = null;
            try {
                report = agents.previsitReport(history);
            } catch (RuntimeException e) {
                log.warn("Pre-visit report not built for intake {}: {}", session.getId(), e.getMessage());
            }
            session.complete(report == null ? null : records.toJson(report), now);
            if (report != null) {
                records.addToMedicationList(patient, report, user, now);
            }
            graphSync.changed(patient.getId());
        }
        sessions.save(session);
    }
}
