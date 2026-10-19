package com.khabar.api.copilot;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.PatientReply;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.insights.RiskScores;
import com.khabar.api.insights.Trends;
import com.khabar.api.pathways.PatientPathway;
import com.khabar.api.pathways.PatientPathwayRepository;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.reminders.AppointmentOutcomeRepository;
import com.khabar.api.reminders.DoseLog;
import com.khabar.api.reminders.DoseLogRepository;
import com.khabar.api.teachback.TeachBackRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One coded row per patient the clinic follows: pathways, risk rules, reading directions, doses, labs and the like,
 * with no name, IC, phone or message text. P1, P2 … are assigned per question and mapped back only in the API.
 */
@Service
public class FollowUpTable {

    /** A row and the patient it stands for. The patient never leaves the API. */
    public record Entry(String code, Patient patient, Map<String, Object> row, RiskScores.Score score, Trends.Summary trends) {
    }

    private final PatientRepository patients;
    private final PatientPathwayRepository pathways;
    private final RiskScores risk;
    private final Trends trends;
    private final DoseLogRepository doses;
    private final PatientReplyRepository replies;
    private final AppointmentOutcomeRepository noShows;
    private final TeachBackRepository teachBacks;
    private final Pathways pathwayService;
    private final AdjustableClock clock;

    public FollowUpTable(PatientRepository patients, PatientPathwayRepository pathways, RiskScores risk, Trends trends,
                         DoseLogRepository doses, PatientReplyRepository replies, AppointmentOutcomeRepository noShows,
                         TeachBackRepository teachBacks, Pathways pathwayService, AdjustableClock clock) {
        this.patients = patients;
        this.pathways = pathways;
        this.risk = risk;
        this.trends = trends;
        this.doses = doses;
        this.replies = replies;
        this.noShows = noShows;
        this.teachBacks = teachBacks;
        this.pathwayService = pathwayService;
        this.clock = clock;
    }

    public List<Entry> build(UUID clinicId) {
        Set<UUID> onPathway = pathways.findByClinicIdAndEndedAtIsNull(clinicId).stream().map(PatientPathway::getPatientId).collect(Collectors.toSet());
        Map<String, Integer> points = risk.pointsFor(clinicId);
        List<Patient> followed = patients.findByClinicId(clinicId).stream()
                .filter(p -> p.getFollowUpStart() != null || onPathway.contains(p.getId()))
                .sorted(Comparator.comparing(Patient::getId))
                .toList();
        List<Entry> entries = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        for (int i = 0; i < followed.size(); i++) {
            Patient p = followed.get(i);
            String code = "P" + (i + 1);
            RiskScores.Score score = risk.score(p.getId(), points);
            Trends.Summary summary = trends.summary(p.getId(), 30);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("language", p.getPreferredLanguage());
            row.put("pathways", pathwayService.active(p.getId()).stream().map(PatientPathway::getPathway).toList());
            row.put("followUpDay", p.followUpDay(today));
            row.put("riskScore", score.score());
            row.put("riskReasons", score.withoutValues().reasons().stream().map(RiskScores.Reason::label).toList());
            for (Trends.Series s : summary.series()) {
                Map<String, Object> reading = new LinkedHashMap<>();
                reading.put("direction", s.direction());
                reading.put("latest", s.points().get(s.points().size() - 1).value());
                reading.put("last7dAverage", s.recent());
                reading.put("previous7dAverage", s.earlier());
                reading.put("target", s.target());
                reading.put("readingsAboveTarget", s.outOfTarget() + " of " + s.points().size());
                row.put(s.key().toLowerCase(), reading);
            }
            List<DoseLog> fortnight = doses.findByPatientIdAndDayGreaterThanEqualOrderByDayAscSentAtAsc(p.getId(), today.minusDays(13));
            if (!fortnight.isEmpty()) {
                row.put("doses14d", Map.of("sent", fortnight.size(),
                        "taken", fortnight.stream().filter(d -> "TAKEN".equals(d.getResponse())).count(),
                        "missed", fortnight.stream().filter(d -> "MISSED".equals(d.getResponse())).count()));
            }
            summary.labs().stream().filter(l -> l.test().equals("HBA1C")).reduce((a, b) -> b)
                    .ifPresent(l -> row.put("latestHbA1c", Map.of("value", l.value(), "takenOn", l.takenOn().toString(), "status", l.status())));
            List<PatientReply> recent = replies.findTop5ByPatientIdOrderByReceivedAtDesc(p.getId());
            if (!recent.isEmpty()) {
                row.put("lastReply", Map.of("level", recent.get(0).getLevel().name(),
                        "daysAgo", Duration.between(recent.get(0).getReceivedAt(), clock.instant()).toDays()));
            }
            row.put("missedVisitNotFollowedUp", !noShows.findByPatientIdAndHandledAtIsNull(p.getId()).isEmpty());
            teachBacks.findTop10ByPatientIdOrderByTakenAtDesc(p.getId()).stream().findFirst()
                    .ifPresent(t -> row.put("lastPlanCheck", t.getCorrect() + " of " + t.getTotal() + " right"));
            row.put("pathwayReviewDue", pathwayService.active(p.getId()).stream().anyMatch(pathwayService::reviewDue));
            entries.add(new Entry(code, p, row, score, summary));
        }
        return entries;
    }
}
