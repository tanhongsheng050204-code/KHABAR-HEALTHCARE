package com.khabar.api.followup;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.clinicops.ClinicOps;
import com.khabar.api.clinicops.ClinicSettings;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.readings.Reading;
import com.khabar.api.readings.ReadingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds "call these patients today": most urgent unhandled replies first, then patients who went quiet. */
@Service
public class CallLists {

    private static final java.time.Duration NO_REPLY_AFTER = java.time.Duration.ofHours(48);
    private static final List<String> REASON_ORDER = List.of("REPLY", "READING", "MISSED_DOSE", "MISSED_APPOINTMENT", "NO_REPLY", "OPEN_CASE");

    private final PatientRepository patients;
    private final PatientReplyRepository replies;
    private final CheckInRepository checkIns;
    private final ReadingRepository readings;
    private final FollowUpCases cases;
    private final ClinicOps clinicOps;
    private final AdjustableClock clock;
    private final com.khabar.api.reminders.AppointmentOutcomeRepository noShows;

    public CallLists(PatientRepository patients, PatientReplyRepository replies, CheckInRepository checkIns,
                     ReadingRepository readings, FollowUpCases cases, ClinicOps clinicOps, AdjustableClock clock,
                     com.khabar.api.reminders.AppointmentOutcomeRepository noShows) {
        this.noShows = noShows;
        this.patients = patients;
        this.replies = replies;
        this.checkIns = checkIns;
        this.readings = readings;
        this.cases = cases;
        this.clinicOps = clinicOps;
        this.clock = clock;
    }

    /**
     * reason is REPLY (a reply needs a call), READING (a home reading is out of range), MISSED_DOSE (the patient's
     * only news is a missed dose) or
     * MISSED_APPOINTMENT (the patient did not come to a booked visit) or
     * NO_REPLY (a check-in has gone unanswered for 48 hours). Within a level, they rank in that order.
     */
    public record CallListItem(UUID patientId, String fullName, String preferredLanguage, TriageLevel level,
                               String urgentReply, String latestReply, Instant latestAt, Integer followUpDay,
                               int unhandledReplies, String reason, FollowUpCases.CaseView followUpCase,
                               String urgentEnglish, String latestEnglish) {

        CallListItem withCase(FollowUpCases.CaseView view) {
            return new CallListItem(patientId, fullName, preferredLanguage, view.level().compareTo(level) < 0 ? view.level() : level,
                    urgentReply, latestReply, latestAt, followUpDay, unhandledReplies, reason, view, urgentEnglish, latestEnglish);
        }
    }

    public record Counts(long red, long watch, long review) {
    }

    /** Server timestamp for the exact queue snapshot rendered to the clinician. */
    public record CallList(List<CallListItem> items, Counts counts, long patientsInFollowUp, Instant snapshotAt,
                           ClinicOps.Coverage coverage) {
    }

    /**
     * The clinic's call list at snapshotAt. With openMissingCases, a listed patient without a follow-up case gets
     * one (what the clinician's own listing does). Without it nothing is written, so a background count, such as
     * the staff alert, cannot start acknowledgement deadlines that no person has seen yet.
     */
    @Transactional
    public CallList build(UUID clinicId, Instant snapshotAt, boolean openMissingCases) {
        LocalDate today = LocalDate.ofInstant(snapshotAt, clock.getZone());

        Map<Patient, List<PatientReply>> byPatient = replies.findByPatientClinicIdAndHandledAtIsNull(clinicId).stream()
                .filter(reply -> !reply.getReceivedAt().isAfter(snapshotAt))
                .filter(PatientReply::needsACall)
                .collect(Collectors.groupingBy(PatientReply::getPatient));

        Map<UUID, CallListItem> byId = new java.util.LinkedHashMap<>();
        byPatient.forEach((patient, open) -> byId.put(patient.getId(), toItem(patient, open, today)));
        // A worrying home reading adds the patient, or replaces their reply item when it is more urgent.
        readings.findByPatientClinicIdAndHandledAtIsNullAndLevelNot(clinicId, TriageLevel.OK).stream()
                .filter(reading -> !reading.getWorkflowReceivedAt().isAfter(snapshotAt))
                .collect(Collectors.groupingBy(r -> r.getPatient().getId()))
                .forEach((id, open) -> {
                    CallListItem reading = readingItem(open, today);
                    CallListItem existing = byId.get(id);
                    if (existing == null || reading.level().compareTo(existing.level()) < 0) {
                        byId.put(id, reading);
                    }
                });
        List<CallListItem> fromReplies = List.copyOf(byId.values());
        java.util.Set<UUID> listed = byId.keySet();
        Instant cutoff = snapshotAt.minus(NO_REPLY_AFTER);
        List<CallListItem> silent = checkIns.findByPatientClinicIdAndStatusAndSentAtBefore(clinicId, CheckIn.Status.SENT, cutoff).stream()
                .filter(c -> !listed.contains(c.getPatient().getId()))
                .collect(Collectors.toMap(c -> c.getPatient().getId(), c -> c, (a, b) -> a.getSentAt().isBefore(b.getSentAt()) ? a : b))
                .values().stream()
                .map(c -> new CallListItem(c.getPatient().getId(), c.getPatient().getFullName(), c.getPatient().getPreferredLanguage(),
                        TriageLevel.REVIEW, null, null, c.getSentAt(), c.getPatient().followUpDay(today), 0, "NO_REPLY", null, null, null))
                .toList();
        java.util.Set<UUID> silentIds = silent.stream().map(CallListItem::patientId).collect(Collectors.toSet());
        Map<UUID, com.khabar.api.reminders.AppointmentOutcome> missedVisits = noShows.findByClinicIdAndHandledAtIsNull(clinicId).stream()
                .filter(outcome -> !outcome.getDetectedAt().isAfter(snapshotAt))
                .filter(outcome -> !listed.contains(outcome.getPatientId()) && !silentIds.contains(outcome.getPatientId()))
                .collect(Collectors.toMap(com.khabar.api.reminders.AppointmentOutcome::getPatientId, o -> o,
                        (a, b) -> a.getAppointmentAt().isAfter(b.getAppointmentAt()) ? a : b));
        List<CallListItem> noShowItems = new java.util.ArrayList<>();
        missedVisits.values().forEach(outcome -> patients.findById(outcome.getPatientId()).ifPresent(p ->
                noShowItems.add(new CallListItem(p.getId(), p.getFullName(), p.getPreferredLanguage(), TriageLevel.REVIEW, null, null,
                        outcome.getAppointmentAt(), p.followUpDay(today), 0, "MISSED_APPOINTMENT", null, null, null))));

        ClinicSettings settings = clinicOps.settingsFor(clinicId);
        Map<UUID, Patient> patientsById = new java.util.HashMap<>();
        byPatient.keySet().forEach(p -> patientsById.put(p.getId(), p));
        readings.findByPatientClinicIdAndHandledAtIsNullAndLevelNot(clinicId, TriageLevel.OK)
                .forEach(r -> patientsById.putIfAbsent(r.getPatient().getId(), r.getPatient()));
        checkIns.findByPatientClinicIdAndStatusAndSentAtBefore(clinicId, CheckIn.Status.SENT, cutoff)
                .forEach(c -> patientsById.putIfAbsent(c.getPatient().getId(), c.getPatient()));
        missedVisits.keySet().forEach(id -> patients.findById(id).ifPresent(p -> patientsById.putIfAbsent(id, p)));
        List<CallListItem> withCases = new java.util.ArrayList<>();
        for (CallListItem item : java.util.stream.Stream.of(fromReplies, silent, noShowItems).flatMap(List::stream).toList()) {
            FollowUpCase open = openMissingCases
                    ? cases.ensureOpen(patientsById.get(item.patientId()), item.level(), item.reason(), snapshotAt)
                    : cases.openCaseFor(item.patientId()).orElse(null);
            withCases.add(open == null ? item : item.withCase(cases.view(open, settings, snapshotAt)));
        }
        java.util.Set<UUID> shown = withCases.stream().map(CallListItem::patientId).collect(Collectors.toSet());
        cases.openCases(clinicId).stream()
                .filter(open -> !shown.contains(open.getPatient().getId()))
                .forEach(open -> withCases.add(new CallListItem(open.getPatient().getId(), open.getPatient().getFullName(),
                        open.getPatient().getPreferredLanguage(), open.getLevel(), null, null, open.getOpenedAt(),
                        open.getPatient().followUpDay(today), 0, "OPEN_CASE", cases.view(open, settings, snapshotAt), null, null)));

        List<CallListItem> items = withCases.stream()
                .sorted(Comparator.comparing(CallListItem::level)
                        .thenComparing(i -> REASON_ORDER.indexOf(i.reason()))
                        .thenComparing(CallListItem::latestAt, Comparator.reverseOrder()))
                .toList();

        Counts counts = new Counts(
                items.stream().filter(i -> i.level() == TriageLevel.RED).count(),
                items.stream().filter(i -> i.level() == TriageLevel.WATCH).count(),
                items.stream().filter(i -> i.level() == TriageLevel.REVIEW).count());
        return new CallList(items, counts, patients.countByClinicIdAndFollowUpStartIsNotNull(clinicId), snapshotAt,
                clinicOps.coverageToday(clinicId));
    }

    private static CallListItem readingItem(List<Reading> open, LocalDate today) {
        Reading latest = open.stream().max(Comparator.comparing(Reading::getMeasuredAt)).orElseThrow();
        Reading urgent = open.stream()
                .min(Comparator.comparing(Reading::getLevel).thenComparing(Reading::getMeasuredAt, Comparator.reverseOrder()))
                .orElseThrow();
        Patient patient = urgent.getPatient();
        return new CallListItem(patient.getId(), patient.getFullName(), patient.getPreferredLanguage(), urgent.getLevel(),
                urgent.getDescription(), latest.getDescription(), latest.getMeasuredAt(), patient.followUpDay(today), open.size(), "READING", null, null, null);
    }

    private static CallListItem toItem(Patient patient, List<PatientReply> open, LocalDate today) {
        PatientReply latest = open.stream().max(Comparator.comparing(PatientReply::getReceivedAt)).orElseThrow();
        PatientReply urgent = open.stream()
                .min(Comparator.comparing(PatientReply::getLevel).thenComparing(PatientReply::getReceivedAt, Comparator.reverseOrder()))
                .orElseThrow();
        // A missed dose in an otherwise cheerful reply still needs a person, so it is never listed as OK.
        TriageLevel level = urgent.getLevel() == TriageLevel.OK ? TriageLevel.REVIEW : urgent.getLevel();
        boolean onlyMissedDoses = open.stream().allMatch(r -> r.isMissedDose() && r.getLevel().compareTo(TriageLevel.REVIEW) >= 0);
        return new CallListItem(patient.getId(), patient.getFullName(), patient.getPreferredLanguage(), level,
                urgent.getText(), latest.getText(), latest.getReceivedAt(), patient.followUpDay(today), open.size(),
                onlyMissedDoses ? "MISSED_DOSE" : "REPLY", null, urgent.getGloss(), latest.getGloss());
    }
}
