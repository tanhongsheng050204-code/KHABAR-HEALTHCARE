package com.khabar.api.insights;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.encounters.Encounter;
import com.khabar.api.encounters.EncounterRepository;
import com.khabar.api.followup.PatientReply;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.labs.Labs;
import com.khabar.api.medications.MedicationItem;
import com.khabar.api.medications.MedicationItemRepository;
import com.khabar.api.pathways.PatientPathway;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientGate;
import com.khabar.api.reminders.AppointmentOutcomeRepository;
import com.khabar.api.reminders.DoseLog;
import com.khabar.api.reminders.DoseLogRepository;
import com.khabar.api.teachback.TeachBack;
import com.khabar.api.teachback.TeachBackRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * "What changed since the last visit", for the doctor before the consultation: one screen built from the record,
 * with no AI involved. Each line says what it counted and since when.
 */
@RestController
public class PreBrief {

    /** level is RED, WATCH, REVIEW or OK, for colour and order; kind names the source. */
    public record Line(String kind, String level, String text) {
    }

    public record Brief(Instant since, Integer daysSince, String headline, List<Line> lines) {
    }

    static final Duration WITHOUT_VISIT = Duration.ofDays(30);

    private final CurrentUser currentUser;
    private final PatientGate gate;
    private final EncounterRepository encounters;
    private final Trends trends;
    private final DoseLogRepository doses;
    private final PatientReplyRepository replies;
    private final Labs labs;
    private final AppointmentOutcomeRepository noShows;
    private final TeachBackRepository teachBacks;
    private final Pathways pathways;
    private final MedicationItemRepository medications;
    private final AdjustableClock clock;

    public PreBrief(CurrentUser currentUser, PatientGate gate, EncounterRepository encounters, Trends trends, DoseLogRepository doses,
                    PatientReplyRepository replies, Labs labs, AppointmentOutcomeRepository noShows, TeachBackRepository teachBacks,
                    Pathways pathways, MedicationItemRepository medications, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.gate = gate;
        this.encounters = encounters;
        this.trends = trends;
        this.doses = doses;
        this.replies = replies;
        this.labs = labs;
        this.noShows = noShows;
        this.teachBacks = teachBacks;
        this.pathways = pathways;
        this.medications = medications;
        this.clock = clock;
    }

    @GetMapping("/api/patients/{id}/prebrief")
    @Transactional
    public Brief prebrief(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = gate.clinical(currentUser.from(jwt), id);
        return build(patient.getId());
    }

    public Brief build(UUID patientId) {
        Instant now = clock.instant();
        Optional<Encounter> last = encounters.findFirstByPatientIdAndStatusOrderByFinalisedAtDesc(patientId, Encounter.Status.FINAL);
        Instant since = last.map(Encounter::getFinalisedAt).orElse(now.minus(WITHOUT_VISIT));
        Integer daysSince = last.map(e -> (int) Duration.between(e.getFinalisedAt(), now).toDays()).orElse(null);
        List<Line> lines = new ArrayList<>();
        List<String> headline = new ArrayList<>();

        int window = (int) Math.max(7, Math.min(180, Duration.between(since, now).toDays() + 1));
        Trends.Summary summary = trends.summary(patientId, window);
        for (Trends.Series s : summary.series()) {
            if (s.key().equals("DIASTOLIC")) {
                continue;
            }
            double latest = s.points().get(s.points().size() - 1).value();
            String word = switch (s.direction()) {
                case Trends.WORSENING -> "rising";
                case Trends.IMPROVING -> "falling";
                case Trends.FLAT -> "steady";
                default -> "too few readings to tell a direction";
            };
            String target = s.target() == null ? "" : ", " + s.outOfTarget() + " of " + s.points().size() + " above target " + fmt(s.target());
            lines.add(new Line(s.key(), Trends.WORSENING.equals(s.direction()) ? "WATCH" : "OK",
                    s.label() + ": " + s.points().size() + " readings, latest " + fmt(latest) + " " + s.unit() + ", " + word + target + "."));
            if (Trends.WORSENING.equals(s.direction())) {
                headline.add(s.label().toLowerCase() + " rising");
            }
        }
        if (summary.series().isEmpty()) {
            lines.add(new Line("READINGS", "REVIEW", "No home readings since the last visit."));
        }

        LocalDate sinceDay = LocalDate.ofInstant(since, clock.getZone());
        List<DoseLog> doseRows = doses.findByPatientIdAndDayGreaterThanEqualOrderByDayAscSentAtAsc(patientId, sinceDay);
        if (!doseRows.isEmpty()) {
            long taken = doseRows.stream().filter(d -> "TAKEN".equals(d.getResponse())).count();
            long missed = doseRows.stream().filter(d -> "MISSED".equals(d.getResponse())).count();
            lines.add(new Line("DOSES", missed >= 3 ? "WATCH" : "OK", "Doses: " + taken + " of " + doseRows.size() + " reminders answered Taken, "
                    + missed + " Missed, " + (doseRows.size() - taken - missed) + " not answered."));
            if (missed >= 3) {
                headline.add(missed + " doses missed");
            }
        }

        List<PatientReply> sinceReplies = replies.findByPatientIdOrderByReceivedAtAsc(patientId).stream()
                .filter(r -> r.getReceivedAt().isAfter(since)).toList();
        if (!sinceReplies.isEmpty()) {
            long urgent = sinceReplies.stream().filter(r -> r.getLevel() == TriageLevel.RED).count();
            long needing = sinceReplies.stream().filter(PatientReply::needsACall).count();
            lines.add(new Line("REPLIES", urgent > 0 ? "RED" : needing > 0 ? "REVIEW" : "OK", "Replies: " + sinceReplies.size() + ", "
                    + needing + " needed a call" + (urgent > 0 ? ", " + urgent + " urgent" : "") + "."));
            if (urgent > 0) {
                headline.add(urgent + " urgent " + (urgent == 1 ? "reply" : "replies"));
            }
        }

        summary.labs().stream().filter(l -> !l.takenOn().isBefore(sinceDay)).forEach(l -> lines.add(new Line("LAB",
                "OUT_OF_TARGET".equals(l.status()) ? "WATCH" : "OK",
                l.label() + " " + fmt(l.value()) + " " + l.unit() + " on " + l.takenOn() + ("OUT_OF_TARGET".equals(l.status()) ? ", outside target." : ".")))
        );

        long missedVisits = noShows.findByPatientIdAndHandledAtIsNull(patientId).size();
        if (missedVisits > 0) {
            lines.add(new Line("VISIT", "WATCH", "Missed " + missedVisits + " booked visit" + (missedVisits == 1 ? "" : "s") + " not yet followed up."));
            headline.add("missed a visit");
        }

        teachBacks.findTop10ByPatientIdOrderByTakenAtDesc(patientId).stream().findFirst()
                .filter(t -> t.getTakenAt().isAfter(since))
                .ifPresent(t -> lines.add(new Line("PLAN_CHECK", t.getCorrect() < t.getTotal() ? "WATCH" : "OK",
                        "Plan check: " + t.getCorrect() + " of " + t.getTotal() + " right" + (t.getMissed() == null ? "." : "; explain again: " + explain(t) + "."))));

        for (PatientPathway p : pathways.active(patientId)) {
            if (pathways.reviewDue(p)) {
                lines.add(new Line("REVIEW", "WATCH", Pathways.DEFINITIONS.get(p.getPathway()).label() + " review was due on " + p.getNextReviewOn() + "."));
            }
        }

        for (MedicationItem m : medications.findByPatientIdOrderByAddedAt(patientId)) {
            if (m.getAddedAt().isAfter(since)) {
                lines.add(new Line("MEDICINE", "REVIEW", "Added to the medicine list: " + m.getName() + (m.getSource() == null ? "" : " (" + m.getSource() + ")") + "."));
            }
            if (m.getStoppedAt() != null && m.getStoppedAt().isAfter(since)) {
                lines.add(new Line("MEDICINE", "REVIEW", "Marked as stopped: " + m.getName() + "."));
            }
        }

        String opening = daysSince == null ? "No signed visit yet; the last 30 days" : "Since the last visit " + daysSince + " days ago";
        String head = opening + (headline.isEmpty() ? ": nothing stands out." : ": " + String.join(", ", headline) + ".");
        lines.sort((a, b) -> Integer.compare(rank(a.level()), rank(b.level())));
        return new Brief(since, daysSince, head, lines);
    }

    private static String explain(TeachBack t) {
        List<String> parts = new ArrayList<>();
        for (String q : t.getMissed().split(",")) {
            parts.add(q.startsWith("times") ? "times a day" : q.startsWith("food") ? "before or after food" : "next visit");
        }
        return String.join(", ", parts);
    }

    private static int rank(String level) {
        return switch (level) {
            case "RED" -> 0;
            case "WATCH" -> 1;
            case "REVIEW" -> 2;
            default -> 3;
        };
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(Math.round(v * 10) / 10.0);
    }
}
