package com.khabar.api.privacy;

import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.encounters.VisitSummaryRepository;
import com.khabar.api.followup.CheckInRepository;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.medications.MedicationItemRepository;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.readings.ReadingRepository;
import com.khabar.api.scheduling.AppointmentRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PDPA rights. A patient can download everything Khabar holds about them and ask their clinic to erase their
 * identity or correct their details. The clinic decides each request and records what it did; erasing removes
 * the identifying fields and the sign-in, and keeps the clinical record for as long as medical records must be
 * kept. The clinic also keeps a register of personal-data breaches.
 */
@RestController
public class PrivacyController {

    static final Set<String> REQUEST_KINDS = Set.of("ERASURE", "CORRECTION");
    static final Set<String> INCIDENT_STEPS = Set.of("CONTAINED", "ASSESSED", "COMMISSIONER_NOTIFIED", "PEOPLE_NOTIFIED", "CLOSED");

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final PatientRepository patients;
    private final MedicationItemRepository medications;
    private final ReadingRepository readings;
    private final PatientReplyRepository replies;
    private final CheckInRepository checkIns;
    private final VisitSummaryRepository summaries;
    private final AppointmentRepository appointments;
    private final CaregiverLinkRepository caregivers;
    private final AuditLog auditLog;
    private final AiReceipts aiReceipts;
    private final PrivacyRequestRepository requests;
    private final PrivacyIncidentRepository incidents;
    private final AdjustableClock clock;

    public PrivacyController(CurrentUser currentUser, ClinicStaffAccess staffAccess, PatientRepository patients,
                             MedicationItemRepository medications, ReadingRepository readings, PatientReplyRepository replies,
                             CheckInRepository checkIns, VisitSummaryRepository summaries, AppointmentRepository appointments,
                             CaregiverLinkRepository caregivers, AuditLog auditLog, AiReceipts aiReceipts,
                             PrivacyRequestRepository requests, PrivacyIncidentRepository incidents, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.patients = patients;
        this.medications = medications;
        this.readings = readings;
        this.replies = replies;
        this.checkIns = checkIns;
        this.summaries = summaries;
        this.appointments = appointments;
        this.caregivers = caregivers;
        this.auditLog = auditLog;
        this.aiReceipts = aiReceipts;
        this.requests = requests;
        this.incidents = incidents;
        this.clock = clock;
    }

    public record RequestInput(String kind, String details) {
    }

    public record RequestView(UUID id, UUID patientId, String patientName, String kind, String details, String status,
                              Instant createdAt, Instant resolvedAt, String resolution) {
    }

    public record ResolveInput(String status, String resolution, Boolean eraseIdentity) {
    }

    public record IncidentInput(String title, String whatHappened, Integer peopleAffected, Instant discoveredAt) {
    }

    public record StepInput(String step, Boolean significantHarm) {
    }

    public record IncidentView(UUID id, String title, String whatHappened, Integer peopleAffected, Instant discoveredAt,
                               Instant containedAt, Instant assessedAt, boolean significantHarm, Instant commissionerNotifiedAt,
                               Instant peopleNotifiedAt, Instant closedAt, String log) {
    }

    // ---- The patient --------------------------------------------------------------------------------------

    /** Everything Khabar holds about the signed-in patient, as one JSON file. */
    @GetMapping("/api/me/export")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal Jwt jwt) {
        Patient patient = requirePatient(jwt);
        UUID id = patient.getId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", clock.instant());
        out.put("about", "Everything Khabar holds about you. Medical records are kept by your clinic as the law requires.");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", patient.getFullName());
        profile.put("icNumber", patient.getIcNumber());
        profile.put("phone", patient.getPhone());
        profile.put("preferredLanguage", patient.getPreferredLanguage());
        profile.put("clinic", patient.getClinic().getName());
        profile.put("allergies", patient.allergyList());
        profile.put("followUpStart", patient.getFollowUpStart());
        profile.put("telegramLinked", patient.getTelegramChatId() != null);
        out.put("profile", profile);
        out.put("medicines", medications.findByPatientIdOrderByAddedAt(id).stream()
                .map(m -> row("name", m.getName(), "kind", m.getKind(), "source", m.getSource(), "addedAt", m.getAddedAt())).toList());
        out.put("readings", readings.findByPatientIdOrderByMeasuredAtAsc(id).stream()
                .map(r -> row("kind", r.getKind(), "measuredAt", r.getMeasuredAt(), "description", r.getDescription(), "level", r.getLevel())).toList());
        out.put("replies", replies.findByPatientIdOrderByReceivedAtAsc(id).stream()
                .map(r -> row("text", r.getText(), "receivedAt", r.getReceivedAt(), "level", r.getLevel())).toList());
        out.put("checkIns", checkIns.findByPatientIdOrderByDueDate(id).stream()
                .map(c -> row("day", c.getDayNumber(), "dueDate", c.getDueDate(), "status", c.getStatus(), "sentAt", c.getSentAt())).toList());
        out.put("summaries", summaries.findReleasedByPatient(id, org.springframework.data.domain.PageRequest.of(0, 100)).stream()
                .map(s -> row("language", s.getLanguage(), "text", s.getText(), "createdAt", s.getCreatedAt())).toList());
        out.put("appointments", appointments.findByPatientIdOrderByStartsAt(id).stream()
                .map(a -> row("startsAt", a.getStartsAt(), "status", a.getStatus(), "reason", a.getReason())).toList());
        out.put("caregivers", caregivers.findByPatientIdAndRevokedAtIsNull(id).stream()
                .map(c -> row("name", c.getCaregiver().getDisplayName(), "scope", c.getScope(), "since", c.getConsentedAt())).toList());
        out.put("whoViewedMyRecord", auditLog.forPatient(id).stream()
                .map(e -> row("at", e.getAt(), "who", e.getActorLabel(), "what", e.getAction())).toList());
        out.put("whatTheAiSaw", aiReceipts.latest(id, 1000));
        out.put("privacyRequests", requests.findByPatientIdOrderByCreatedAtDesc(id).stream().map(r -> view(r, patient)).toList());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"khabar-my-data.json\"")
                .body(out);
    }

    @GetMapping("/api/me/privacy-requests")
    @Transactional(readOnly = true)
    public List<RequestView> myRequests(@AuthenticationPrincipal Jwt jwt) {
        Patient patient = requirePatient(jwt);
        return requests.findByPatientIdOrderByCreatedAtDesc(patient.getId()).stream().map(r -> view(r, patient)).toList();
    }

    @PostMapping("/api/me/privacy-requests")
    @Transactional
    public RequestView request(@RequestBody RequestInput input, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = requirePatient(jwt);
        if (input == null || input.kind() == null || !REQUEST_KINDS.contains(input.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose erasure or correction.");
        }
        String details = input.details() == null ? null : input.details().trim();
        if ("CORRECTION".equals(input.kind()) && (details == null || details.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say what needs correcting.");
        }
        if (details != null && details.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the details under 2,000 characters.");
        }
        boolean open = requests.findByPatientIdOrderByCreatedAtDesc(patient.getId()).stream()
                .anyMatch(r -> r.getKind().equals(input.kind()) && r.getStatus().equals("OPEN"));
        if (open) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have an open request of this kind.");
        }
        PrivacyRequest saved = requests.save(new PrivacyRequest(patient.getClinic().getId(), patient.getId(), input.kind(), details, clock.instant()));
        return view(saved, patient);
    }

    // ---- The clinic ---------------------------------------------------------------------------------------

    @GetMapping("/api/clinic/privacy-requests")
    @Transactional(readOnly = true)
    public List<RequestView> clinicRequests(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        return requests.findByClinicIdOrderByCreatedAtDesc(actor.getClinic().getId()).stream()
                .map(r -> view(r, patients.findById(r.getPatientId()).orElse(null)))
                .toList();
    }

    @PostMapping("/api/clinic/privacy-requests/{id}/resolve")
    @Transactional
    public RequestView resolve(@PathVariable UUID id, @RequestBody ResolveInput input, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        PrivacyRequest request = requests.findById(id)
                .filter(r -> r.getClinicId().equals(actor.getClinic().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"OPEN".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is already decided.");
        }
        if (input == null || !("DONE".equals(input.status()) || "DECLINED".equals(input.status()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mark the request done or declined.");
        }
        String resolution = input.resolution() == null ? "" : input.resolution().trim();
        if (resolution.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tell the patient what was done or why not, in at least 10 characters.");
        }
        Patient patient = patients.findById(request.getPatientId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if ("DONE".equals(input.status()) && "ERASURE".equals(request.getKind()) && Boolean.TRUE.equals(input.eraseIdentity())) {
            caregivers.findByPatientIdAndRevokedAtIsNull(patient.getId()).forEach(link -> link.revoke(clock.instant()));
            patient.eraseIdentity("Erased patient " + patient.getId().toString().substring(0, 8));
            patients.save(patient);
        }
        request.resolve(input.status(), resolution.length() > 1000 ? resolution.substring(0, 1000) : resolution, actor.getId(), clock.instant());
        return view(requests.save(request), patient);
    }

    @GetMapping("/api/clinic/incidents")
    @Transactional(readOnly = true)
    public List<IncidentView> incidents(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        return incidents.findByClinicIdOrderByDiscoveredAtDesc(actor.getClinic().getId()).stream().map(PrivacyController::view).toList();
    }

    @PostMapping("/api/clinic/incidents")
    @Transactional
    public IncidentView report(@RequestBody IncidentInput input, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        if (input == null || input.title() == null || input.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the incident a short title.");
        }
        if (input.peopleAffected() != null && input.peopleAffected() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The number of people affected cannot be negative.");
        }
        String title = input.title().trim();
        Instant discovered = input.discoveredAt() == null ? clock.instant() : input.discoveredAt();
        return view(incidents.save(new PrivacyIncident(actor.getClinic().getId(), title.length() > 200 ? title.substring(0, 200) : title,
                input.whatHappened(), input.peopleAffected(), discovered, actor.getId())));
    }

    @PostMapping("/api/clinic/incidents/{id}/steps")
    @Transactional
    public IncidentView step(@PathVariable UUID id, @RequestBody StepInput input, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        PrivacyIncident incident = incidents.findById(id)
                .filter(i -> i.getClinicId().equals(actor.getClinic().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (input == null || input.step() == null || !INCIDENT_STEPS.contains(input.step())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown step.");
        }
        if ("ASSESSED".equals(input.step()) && input.significantHarm() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say whether significant harm to the people affected is likely.");
        }
        if ("CLOSED".equals(input.step()) && (incident.getContainedAt() == null || incident.getAssessedAt() == null
                || incident.getCommissionerNotifiedAt() == null || (incident.isSignificantHarm() && incident.getPeopleNotifiedAt() == null))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finish the earlier steps before closing the incident.");
        }
        incident.complete(input.step(), actor.getDisplayName(), clock.instant(), input.significantHarm());
        return view(incidents.save(incident));
    }

    private static IncidentView view(PrivacyIncident i) {
        return new IncidentView(i.getId(), i.getTitle(), i.getWhatHappened(), i.getPeopleAffected(), i.getDiscoveredAt(),
                i.getContainedAt(), i.getAssessedAt(), i.isSignificantHarm(), i.getCommissionerNotifiedAt(), i.getPeopleNotifiedAt(),
                i.getClosedAt(), i.getLog());
    }

    private static RequestView view(PrivacyRequest r, Patient patient) {
        return new RequestView(r.getId(), r.getPatientId(), patient == null ? null : patient.getFullName(), r.getKind(), r.getDetails(),
                r.getStatus(), r.getCreatedAt(), r.getResolvedAt(), r.getResolution());
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    private Patient requirePatient(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the patient can do this.");
        }
        return patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));
    }

    private AppUser requireManager(Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !staffAccess.canManageStaff(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clinic doctors or administrators handle privacy requests and incidents.");
        }
        return actor;
    }
}
