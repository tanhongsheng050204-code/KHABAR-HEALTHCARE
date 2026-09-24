package com.khabar.api.followup;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.clinicops.ClinicActivity;
import com.khabar.api.clinicops.ClinicOps;
import com.khabar.api.clinicops.ClinicSettings;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.patients.Patient;
import com.khabar.api.readings.Reading;
import com.khabar.api.readings.ReadingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The follow-up case lifecycle. Every change is written to the case's own history and to the clinic activity
 * log (by case reference, never by patient name). Repeating an action that changes nothing records nothing.
 */
@Service
public class FollowUpCases {

    static final int MIN_URGENT_NOTE = 10;

    private final FollowUpCaseRepository cases;
    private final FollowUpCaseEventRepository events;
    private final PatientReplyRepository replies;
    private final ReadingRepository readings;
    private final CheckInRepository checkIns;
    private final AuditLog auditLog;
    private final ClinicOps clinicOps;
    private final ClinicStaffAccess staffAccess;
    private final AppUserRepository users;
    private final AdjustableClock clock;

    public FollowUpCases(FollowUpCaseRepository cases, FollowUpCaseEventRepository events, PatientReplyRepository replies,
                         ReadingRepository readings, CheckInRepository checkIns, AuditLog auditLog, ClinicOps clinicOps,
                         ClinicStaffAccess staffAccess, AppUserRepository users, AdjustableClock clock) {
        this.cases = cases;
        this.events = events;
        this.replies = replies;
        this.readings = readings;
        this.checkIns = checkIns;
        this.auditLog = auditLog;
        this.clinicOps = clinicOps;
        this.staffAccess = staffAccess;
        this.users = users;
        this.clock = clock;
    }

    public record CaseView(UUID id, CaseStatus status, TriageLevel level, String reason, UUID ownerId, String ownerName,
                           Instant openedAt, Instant acknowledgedAt, Instant acknowledgeBy, boolean overdue,
                           Instant escalatedAt, String escalatedTo, int contactAttempts, Instant lastAttemptAt,
                           Instant closedAt, ClosureReason closureReason) {
    }

    public record EventView(FollowUpCaseEvent.Action action, CaseStatus from, CaseStatus to, String by, String note, Instant at) {
    }

    /** The patient's open case, opened now if they have none; its level rises if this listing is more urgent. */
    public FollowUpCase ensureOpen(Patient patient, TriageLevel level, String reason, Instant now) {
        return cases.findByPatientIdAndClosedAtIsNull(patient.getId()).map(open -> {
            CaseStatus before = open.getStatus();
            if (open.raiseTo(level)) {
                events.save(new FollowUpCaseEvent(open.getId(), FollowUpCaseEvent.Action.LEVEL_RAISED, before, open.getStatus(),
                        null, "Khabar", "Now " + level.name().toLowerCase(), now));
            }
            return open;
        }).orElseGet(() -> {
            FollowUpCase created = cases.save(new FollowUpCase(patient.getClinic(), patient, level, reason, now));
            events.save(new FollowUpCaseEvent(created.getId(), FollowUpCaseEvent.Action.OPENED, null, CaseStatus.NEW,
                    null, "Khabar", reason, now));
            return created;
        });
    }

    public List<FollowUpCase> openCases(UUID clinicId) {
        return cases.findByClinicIdAndClosedAtIsNull(clinicId);
    }

    public CaseView view(FollowUpCase c, ClinicSettings settings, Instant now) {
        Instant due = c.getOpenedAt().plus(Duration.ofMinutes(settings.ackMinutes(c.getLevel())));
        boolean overdue = c.isOpen() && c.getAcknowledgedAt() == null && now.isAfter(due);
        return new CaseView(c.getId(), c.getStatus(), c.getLevel(), c.getReason(), c.getOwnerId(), nameOf(c.getOwnerId()),
                c.getOpenedAt(), c.getAcknowledgedAt(), due, overdue, c.getEscalatedAt(), nameOf(c.getEscalatedTo()),
                c.getContactAttempts(), c.getLastAttemptAt(), c.getClosedAt(), c.getClosureReason());
    }

    public CaseView view(FollowUpCase c) {
        return view(c, clinicOps.settingsFor(c.getClinic().getId()), clock.instant());
    }

    public FollowUpCase caseFor(AppUser actor, UUID caseId) {
        return cases.findById(caseId)
                .filter(c -> c.getClinic().getId().equals(actor.getClinic().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That follow-up case was not found."));
    }

    public List<EventView> history(FollowUpCase c) {
        return events.findByCaseIdOrderByIdAsc(c.getId()).stream()
                .map(e -> new EventView(e.getAction(), e.getFromStatus(), e.getToStatus(), e.getActorLabel(), e.getNote(), e.getAt()))
                .toList();
    }

    public CaseView assign(AppUser actor, FollowUpCase c, UUID ownerId) {
        requireOpen(c);
        UUID owner = ownerId == null ? actor.getId() : ownerId;
        AppUser ownerUser = users.findById(owner)
                .filter(u -> u.getClinic() != null && u.getClinic().getId().equals(c.getClinic().getId()) && staffAccess.canManageFollowUp(u))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cases can only be assigned to a doctor or nurse at this clinic."));
        if (owner.equals(c.getOwnerId())) {
            return view(c);
        }
        CaseStatus before = c.getStatus();
        c.assign(owner);
        log(actor, c, FollowUpCaseEvent.Action.ASSIGNED, before, "To " + ownerUser.getDisplayName(), ClinicActivity.Action.CASE_ASSIGNED);
        return view(c);
    }

    public CaseView acknowledge(AppUser actor, FollowUpCase c) {
        requireOpen(c);
        CaseStatus before = c.getStatus();
        if (c.acknowledge(actor.getId(), clock.instant())) {
            log(actor, c, FollowUpCaseEvent.Action.ACKNOWLEDGED, before, null, ClinicActivity.Action.CASE_ACKNOWLEDGED);
        }
        return view(c);
    }

    public CaseView contact(AppUser actor, FollowUpCase c, boolean reached, String note) {
        requireOpen(c);
        CaseStatus before = c.getStatus();
        c.attempt(reached, actor.getId(), clock.instant());
        log(actor, c, reached ? FollowUpCaseEvent.Action.CONTACT_REACHED : FollowUpCaseEvent.Action.CONTACT_NO_ANSWER, before,
                trim(note), ClinicActivity.Action.CASE_CONTACT_ATTEMPT);
        return view(c);
    }

    public CaseView escalate(AppUser actor, FollowUpCase c, UUID toUserId, String note) {
        requireOpen(c);
        if (note == null || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say why the case is being escalated.");
        }
        if (toUserId != null) {
            users.findById(toUserId)
                    .filter(u -> u.getClinic() != null && u.getClinic().getId().equals(c.getClinic().getId())
                            && staffAccess.hasRole(u, ClinicStaffRole.DOCTOR))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escalate to a doctor at this clinic."));
        }
        CaseStatus before = c.getStatus();
        c.escalate(toUserId, actor.getId(), clock.instant());
        log(actor, c, FollowUpCaseEvent.Action.ESCALATED, before, trim(note), ClinicActivity.Action.CASE_ESCALATED);
        return view(c);
    }

    /**
     * Closes the case with a structured reason and resolves what the clinician saw: replies, readings and check-ins
     * up to observedThrough. Anything newer stays open and reopens the case at the next listing. Closing an
     * already-closed case changes nothing. An urgent case needs a note, and cannot be closed as unreachable
     * unless it was escalated first.
     */
    public CaseView close(AppUser actor, FollowUpCase c, ClosureReason reason, String note, Instant observedThrough) {
        if (!c.isOpen()) {
            return view(c);
        }
        if (reason == null || reason == ClosureReason.CONTACT_RECORDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose why the case is being closed.");
        }
        Instant now = clock.instant();
        if (observedThrough == null || observedThrough.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh the call list before closing a case.");
        }
        String trimmed = trim(note);
        if (c.getLevel() == TriageLevel.RED && (trimmed == null || trimmed.length() < MIN_URGENT_NOTE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An urgent case needs a note of at least " + MIN_URGENT_NOTE + " characters.");
        }
        if (reason == ClosureReason.UNABLE_TO_CONTACT_AFTER_ATTEMPTS) {
            if (c.getContactAttempts() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record at least one call attempt first.");
            }
            if (c.getLevel() == TriageLevel.RED && c.getEscalatedAt() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Escalate an urgent case before closing it as unreachable.");
            }
        }
        return closeResolving(actor, c, reason, trimmed, observedThrough, now);
    }

    /** The older one-step "record contact": resolves what was seen and closes the patient's case, if any. */
    public void recordContact(AppUser actor, Patient patient, Instant observedThrough) {
        Instant now = clock.instant();
        FollowUpCase open = cases.findByPatientIdAndClosedAtIsNull(patient.getId()).orElse(null);
        if (open != null) {
            closeResolving(actor, open, ClosureReason.CONTACT_RECORDED, null, observedThrough, now);
        } else {
            resolveSources(actor, patient.getId(), observedThrough, now);
        }
    }

    private CaseView closeResolving(AppUser actor, FollowUpCase c, ClosureReason reason, String note, Instant observedThrough, Instant now) {
        resolveSources(actor, c.getPatient().getId(), observedThrough, now);
        CaseStatus before = c.getStatus();
        c.close(reason, note, actor.getId(), now);
        log(actor, c, FollowUpCaseEvent.Action.CLOSED, before, reason.name() + (note == null ? "" : ": " + note), ClinicActivity.Action.CASE_CLOSED);
        return view(c);
    }

    /** Marks handled what was on the clinician's screen: never anything received after observedThrough. */
    public Resolved resolveSources(AppUser actor, UUID patientId, Instant observedThrough, Instant now) {
        List<PatientReply> open = replies.findByPatientIdAndHandledAtIsNull(patientId).stream()
                .filter(reply -> !reply.getReceivedAt().isAfter(observedThrough)).toList();
        open.forEach(r -> r.markHandled(actor.getId(), now));
        List<Reading> openReadings = readings.findByPatientIdAndHandledAtIsNull(patientId).stream()
                .filter(reading -> !reading.getWorkflowReceivedAt().isAfter(observedThrough)).toList();
        openReadings.forEach(r -> r.markHandled(actor.getId(), now));
        List<CheckIn> unanswered = checkIns.findByPatientIdAndStatus(patientId, CheckIn.Status.SENT).stream()
                .filter(checkIn -> checkIn.getSentAt() != null && !checkIn.getSentAt().isAfter(observedThrough)).toList();
        unanswered.forEach(CheckIn::markAnswered);
        if (!open.isEmpty() || !unanswered.isEmpty() || !openReadings.isEmpty()) {
            auditLog.record(actor, patientId, AuditAction.CALLED_ABOUT_REPLY);
        }
        return new Resolved(open.size(), openReadings.size(), unanswered.size());
    }

    public record Resolved(int replies, int readings, int checkIns) {
    }

    private void log(AppUser actor, FollowUpCase c, FollowUpCaseEvent.Action action, CaseStatus before, String note,
                     ClinicActivity.Action activity) {
        events.save(new FollowUpCaseEvent(c.getId(), action, before, c.getStatus(), actor.getId(), actor.getDisplayName(), note, clock.instant()));
        clinicOps.record(actor, activity, ClinicOps.caseRef(c.getId()));
    }

    private static void requireOpen(FollowUpCase c) {
        if (!c.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This case is already closed.");
        }
    }

    private static String trim(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String t = note.trim();
        return t.length() > 1000 ? t.substring(0, 1000) : t;
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : users.findById(userId).map(AppUser::getDisplayName).orElse("Former staff member");
    }
}
