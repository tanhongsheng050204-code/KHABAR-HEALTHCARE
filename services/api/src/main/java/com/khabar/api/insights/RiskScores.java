package com.khabar.api.insights;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CheckIn;
import com.khabar.api.followup.CheckInRepository;
import com.khabar.api.labs.Labs;
import com.khabar.api.pathways.PatientPathway;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.reminders.AppointmentOutcomeRepository;
import com.khabar.api.reminders.DoseLog;
import com.khabar.api.reminders.DoseLogRepository;
import com.khabar.api.teachback.TeachBackRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A follow-up risk score anyone can read: points for things that have gone wrong lately, each with its reason. It
 * only orders patients within the same urgency on the call list; it never moves an urgent reply down. The points
 * are the clinic's to change. It is a way to sort a list, not a prediction.
 */
@Service
public class RiskScores {

    public record Rule(String key, String label, int defaultPoints) {
    }

    public static final List<Rule> RULES = List.of(
            new Rule("MISSED_DOSES", "Each dose answered Missed in the last 7 days (up to 3)", 1),
            new Rule("UNANSWERED_DOSES", "3 or more dose reminders unanswered in the last 7 days", 1),
            new Rule("GLUCOSE_WORSENING", "Blood sugar rising: last 7 days higher than the 7 before", 2),
            new Rule("GLUCOSE_ABOVE_TARGET", "Latest blood sugar above the pathway target", 1),
            new Rule("BP_WORSENING", "Blood pressure rising: last 7 days higher than the 7 before", 2),
            new Rule("BP_ABOVE_TARGET", "Latest blood pressure above the pathway target", 1),
            new Rule("NO_REPLY", "A check-in unanswered for 48 hours", 2),
            new Rule("MISSED_VISIT", "Missed a booked visit in the last 30 days", 2),
            new Rule("HBA1C_ABOVE_TARGET", "Latest HbA1c above target", 1),
            new Rule("REVIEW_DUE", "Pathway review date reached", 1),
            new Rule("PLAN_NOT_UNDERSTOOD", "Last plan check had wrong answers", 1));

    public record Reason(String key, String label, int points) {
    }

    public record Score(UUID patientId, int score, List<Reason> reasons) {
    }

    public record Weight(String key, String label, int defaultPoints, int points) {
    }

    private final Trends trends;
    private final DoseLogRepository doses;
    private final CheckInRepository checkIns;
    private final AppointmentOutcomeRepository noShows;
    private final TeachBackRepository teachBacks;
    private final Pathways pathways;
    private final RiskWeightsRepository weights;
    private final AdjustableClock clock;

    public RiskScores(Trends trends, DoseLogRepository doses, CheckInRepository checkIns, AppointmentOutcomeRepository noShows,
                      TeachBackRepository teachBacks, Pathways pathways, RiskWeightsRepository weights, AdjustableClock clock) {
        this.trends = trends;
        this.doses = doses;
        this.checkIns = checkIns;
        this.noShows = noShows;
        this.teachBacks = teachBacks;
        this.pathways = pathways;
        this.weights = weights;
        this.clock = clock;
    }

    public Score score(UUID patientId, UUID clinicId) {
        return score(patientId, pointsFor(clinicId));
    }

    public Score score(UUID patientId, Map<String, Integer> points) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        List<Reason> reasons = new ArrayList<>();

        List<DoseLog> week = doses.findByPatientIdAndDayGreaterThanEqualOrderByDayAscSentAtAsc(patientId, today.minusDays(6));
        long missed = week.stream().filter(d -> "MISSED".equals(d.getResponse())).count();
        if (missed > 0) {
            add(reasons, points, "MISSED_DOSES", (int) Math.min(3, missed), missed + " missed dose" + (missed == 1 ? "" : "s") + " this week");
        }
        long unanswered = week.stream().filter(d -> d.getResponse() == null && d.getDay().isBefore(today)).count();
        if (unanswered >= 3) {
            add(reasons, points, "UNANSWERED_DOSES", 1, unanswered + " dose reminders unanswered this week");
        }

        Trends.Summary summary = trends.summary(patientId, 30);
        for (Trends.Series s : summary.series()) {
            String latest = s.points().isEmpty() ? null : number(s.points().get(s.points().size() - 1).value());
            boolean above = s.target() != null && !s.points().isEmpty() && s.points().get(s.points().size() - 1).value() > s.target();
            if (s.key().equals("GLUCOSE")) {
                if (Trends.WORSENING.equals(s.direction())) {
                    add(reasons, points, "GLUCOSE_WORSENING", 1, "Blood sugar rising (" + s.earlier() + " → " + s.recent() + " mmol/L)");
                }
                if (above) {
                    add(reasons, points, "GLUCOSE_ABOVE_TARGET", 1, "Latest blood sugar " + latest + " above target " + number(s.target()));
                }
            }
            if (s.key().equals("SYSTOLIC")) {
                if (Trends.WORSENING.equals(s.direction())) {
                    add(reasons, points, "BP_WORSENING", 1, "Blood pressure rising (" + s.earlier() + " → " + s.recent() + " mmHg top)");
                }
                if (above) {
                    add(reasons, points, "BP_ABOVE_TARGET", 1, "Latest blood pressure top " + latest + " above target " + number(s.target()));
                }
            }
        }

        boolean quiet = checkIns.findByPatientIdAndStatus(patientId, CheckIn.Status.SENT).stream()
                .anyMatch(c -> c.getSentAt() != null && c.getSentAt().isBefore(now.minus(Duration.ofHours(48))));
        if (quiet) {
            add(reasons, points, "NO_REPLY", 1, "Check-in unanswered for 48 hours");
        }
        if (noShows.existsByPatientIdAndAppointmentAtAfter(patientId, now.minus(Duration.ofDays(30)))) {
            add(reasons, points, "MISSED_VISIT", 1, "Missed a booked visit this month");
        }
        summary.labs().stream().filter(l -> l.test().equals("HBA1C")).reduce((a, b) -> b)
                .filter(l -> "OUT_OF_TARGET".equals(l.status()))
                .ifPresent(l -> add(reasons, points, "HBA1C_ABOVE_TARGET", 1, "HbA1c " + number(l.value()) + "% above target " + number(l.target()) + "%"));
        for (PatientPathway p : pathways.active(patientId)) {
            if (pathways.reviewDue(p)) {
                add(reasons, points, "REVIEW_DUE", 1, Pathways.DEFINITIONS.get(p.getPathway()).label() + " review due since " + p.getNextReviewOn());
                break;
            }
        }
        teachBacks.findTop10ByPatientIdOrderByTakenAtDesc(patientId).stream().findFirst()
                .filter(t -> t.getCorrect() < t.getTotal())
                .ifPresent(t -> add(reasons, points, "PLAN_NOT_UNDERSTOOD", 1, "Plan check: " + t.getCorrect() + " of " + t.getTotal() + " right"));

        reasons.removeIf(r -> r.points() <= 0);
        reasons.sort(Comparator.comparingInt(Reason::points).reversed());
        return new Score(patientId, reasons.stream().mapToInt(Reason::points).sum(), reasons);
    }

    private static void add(List<Reason> reasons, Map<String, Integer> points, String key, int times, String label) {
        reasons.add(new Reason(key, label, times * points.getOrDefault(key, 0)));
    }

    // ---- Weights --------------------------------------------------------------------------------------------

    public Map<String, Integer> pointsFor(UUID clinicId) {
        Map<String, Integer> points = new LinkedHashMap<>();
        RULES.forEach(r -> points.put(r.key(), r.defaultPoints()));
        if (clinicId != null) {
            weights.findById(clinicId).ifPresent(w -> parse(w.getWeights()).forEach(points::put));
        }
        return points;
    }

    public List<Weight> weights(UUID clinicId) {
        Map<String, Integer> points = pointsFor(clinicId);
        return RULES.stream().map(r -> new Weight(r.key(), r.label(), r.defaultPoints(), points.get(r.key()))).toList();
    }

    public List<Weight> updateWeights(UUID clinicId, Map<String, Integer> changes, UUID by) {
        Map<String, Integer> points = pointsFor(clinicId);
        changes.forEach((key, value) -> {
            if (RULES.stream().noneMatch(r -> r.key().equals(key))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown rule " + key + ".");
            }
            if (value == null || value < 0 || value > 10) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points must be between 0 and 10.");
            }
            points.put(key, value);
        });
        RiskWeights row = weights.findById(clinicId).orElseGet(() -> new RiskWeights(clinicId));
        StringBuilder text = new StringBuilder();
        points.forEach((k, v) -> text.append(text.isEmpty() ? "" : ",").append(k).append('=').append(v));
        row.update(text.toString(), by, clock.instant());
        weights.save(row);
        return weights(clinicId);
    }

    static Map<String, Integer> parse(String text) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split(",")) {
            String[] kv = part.split("=");
            if (kv.length == 2) {
                try {
                    out.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                    // A damaged entry falls back to the default.
                }
            }
        }
        return out;
    }

    private static String number(Double v) {
        return v == null ? "?" : v == Math.rint(v) ? Long.toString(v.longValue()) : Double.toString(Math.round(v * 10) / 10.0);
    }
}
