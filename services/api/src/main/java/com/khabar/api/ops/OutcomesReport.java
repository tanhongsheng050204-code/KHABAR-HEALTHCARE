package com.khabar.api.ops;

import com.khabar.api.clinicops.ClinicOps;
import com.khabar.api.clinicops.ClinicSettings;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CheckIn;
import com.khabar.api.followup.FollowUpCase;
import com.khabar.api.followup.PatientReply;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.insights.Trends;
import com.khabar.api.labs.LabResult;
import com.khabar.api.pathways.PatientPathwayRepository;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.reminders.AppointmentOutcome;
import com.khabar.api.reminders.DoseLog;
import com.khabar.api.teachback.TeachBack;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One month of follow-up for the clinic's owner, funder or insurer: counts from the clinic's own records, each
 * with what it is out of. No projections, no money and no comparison with other clinics; no patient is named.
 */
@RestController
public class OutcomesReport {

    /** value of `of`; null `of` means a plain count. */
    public record Line(String key, String label, long value, Long of, String note) {
    }

    public record Section(String title, List<Line> lines) {
    }

    public record Report(String clinicName, String month, LocalDate from, LocalDate to, boolean monthInProgress, List<Section> sections,
                         Instant generatedAt) {
    }

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final EntityManager em;
    private final ClinicOps clinicOps;
    private final PatientRepository patients;
    private final PatientPathwayRepository pathways;
    private final Trends trends;
    private final AdjustableClock clock;

    public OutcomesReport(CurrentUser currentUser, ClinicStaffAccess staffAccess, EntityManager em, ClinicOps clinicOps,
                          PatientRepository patients, PatientPathwayRepository pathways, Trends trends, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.em = em;
        this.clinicOps = clinicOps;
        this.patients = patients;
        this.pathways = pathways;
        this.trends = trends;
        this.clock = clock;
    }

    @GetMapping("/api/clinic/report")
    @Transactional(readOnly = true)
    public Report report(@RequestParam(required = false) String month, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !staffAccess.canManageStaff(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The outcomes report is for doctors and clinic administrators.");
        }
        ZoneId zone = clock.getZone();
        YearMonth current = YearMonth.from(LocalDate.now(clock));
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? current : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a month like 2026-10.");
        }
        if (ym.isAfter(current) || ym.isBefore(current.minusMonths(24))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a month in the last two years.");
        }
        UUID clinicId = actor.getClinic().getId();
        Instant from = ym.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Instant end = to.isAfter(clock.instant()) ? clock.instant() : to;
        List<Section> sections = new ArrayList<>();

        // Who was followed
        Set<UUID> onPathway = pathways.findByClinicIdAndEndedAtIsNull(clinicId).stream().map(p -> p.getPatientId()).collect(Collectors.toSet());
        List<Patient> followed = patients.findByClinicId(clinicId).stream()
                .filter(p -> p.getFollowUpStart() != null && p.getFollowUpStart().isBefore(ym.plusMonths(1).atDay(1)) || onPathway.contains(p.getId()))
                .toList();
        Map<String, Long> byPathway = new LinkedHashMap<>();
        pathways.findByClinicIdAndEndedAtIsNull(clinicId).forEach(p -> byPathway.merge(p.getPathway(), 1L, Long::sum));
        List<Line> people = new ArrayList<>();
        people.add(new Line("followed", "Patients in follow-up or on a care pathway", followed.size(), null, null));
        byPathway.forEach((k, v) -> people.add(new Line("pathway_" + k, "On the " + Pathways.DEFINITIONS.get(k).label().toLowerCase() + " pathway", v, null, null)));
        sections.add(new Section("Who was followed", people));

        // Reaching patients
        List<CheckIn> checkIns = list("select c from CheckIn c where c.patient.clinic.id = :clinic and c.sentAt >= :from and c.sentAt < :to", CheckIn.class, clinicId, from, to);
        List<PatientReply> replies = list("select r from PatientReply r where r.patient.clinic.id = :clinic and r.receivedAt >= :from and r.receivedAt < :to", PatientReply.class, clinicId, from, to);
        List<Line> reach = new ArrayList<>();
        reach.add(new Line("checkInsAnswered", "Check-ins answered", checkIns.stream().filter(c -> c.getStatus() == CheckIn.Status.ANSWERED).count(),
                (long) checkIns.size(), checkIns.isEmpty() ? "No check-ins were sent this month." : null));
        reach.add(new Line("replies", "Replies from patients and caregivers", replies.size(), null, null));
        reach.add(new Line("urgent", "Replies that got the 999 advice and went to the top of the call list",
                replies.stream().filter(r -> r.getLevel() == TriageLevel.RED).count(), null, null));
        sections.add(new Section("Reaching patients", reach));

        // Clinic response
        ClinicSettings settings = clinicOps.settingsFor(clinicId);
        List<FollowUpCase> opened = list("select c from FollowUpCase c where c.clinic.id = :clinic and c.openedAt >= :from and c.openedAt < :to", FollowUpCase.class, clinicId, from, to);
        List<Long> ack = opened.stream().filter(c -> c.getAcknowledgedAt() != null)
                .map(c -> Duration.between(c.getOpenedAt(), c.getAcknowledgedAt()).toMinutes()).sorted().toList();
        long onTime = opened.stream().filter(c -> c.getAcknowledgedAt() != null
                && Duration.between(c.getOpenedAt(), c.getAcknowledgedAt()).toMinutes() <= settings.ackMinutes(c.getLevel())).count();
        List<FollowUpCase> closed = list("select c from FollowUpCase c where c.clinic.id = :clinic and c.closedAt >= :from and c.closedAt < :to", FollowUpCase.class, clinicId, from, to);
        List<Line> response = new ArrayList<>();
        response.add(new Line("ackOnTime", "Cases acknowledged within the clinic's own time limit", onTime, (long) opened.size(),
                opened.isEmpty() ? "No cases were opened this month." : null));
        response.add(new Line("medianAck", "Median minutes to acknowledge a case", ack.isEmpty() ? 0 : ack.get(ack.size() / 2), null,
                ack.isEmpty() ? "No case was acknowledged this month." : null));
        response.add(new Line("closedAfterContact", "Cases closed after the patient was reached or reviewed",
                closed.stream().filter(c -> c.getClosureReason() != null && !c.getClosureReason().name().startsWith("UNABLE")).count(), (long) closed.size(), null));
        sections.add(new Section("Clinic response", response));

        // Medicines and visits
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay = ym.atEndOfMonth();
        List<DoseLog> doses = em.createQuery("select d from DoseLog d, Patient p where p.id = d.patientId and p.clinic.id = :clinic "
                        + "and d.day >= :from and d.day <= :to", DoseLog.class)
                .setParameter("clinic", clinicId).setParameter("from", firstDay).setParameter("to", lastDay).getResultList();
        List<AppointmentOutcome> noShows = list("select o from AppointmentOutcome o where o.clinicId = :clinic and o.appointmentAt >= :from and o.appointmentAt < :to",
                AppointmentOutcome.class, clinicId, from, to);
        List<TeachBack> checks = list("select t from TeachBack t where t.clinicId = :clinic and t.takenAt >= :from and t.takenAt < :to", TeachBack.class, clinicId, from, to);
        List<Line> medicines = new ArrayList<>();
        medicines.add(new Line("dosesTaken", "Dose reminders answered Taken", doses.stream().filter(d -> "TAKEN".equals(d.getResponse())).count(),
                (long) doses.size(), doses.isEmpty() ? "No dose reminders this month." : "Unanswered reminders count as not taken."));
        medicines.add(new Line("dosesMissed", "Dose reminders answered Missed", doses.stream().filter(d -> "MISSED".equals(d.getResponse())).count(), (long) doses.size(), null));
        medicines.add(new Line("noShowsFollowedUp", "Missed visits followed up by the clinic", noShows.stream().filter(o -> o.getHandledAt() != null).count(),
                (long) noShows.size(), noShows.isEmpty() ? "No booked visit was missed this month." : null));
        medicines.add(new Line("planChecks", "Plan checks answered fully right", checks.stream().filter(t -> t.getCorrect() == t.getTotal()).count(),
                (long) checks.size(), checks.isEmpty() ? "No patient checked their plan this month." : null));
        sections.add(new Section("Medicines and visits", medicines));

        // Clinical picture
        Set<UUID> followedIds = followed.stream().map(Patient::getId).collect(Collectors.toSet());
        List<LabResult> labs = em.createQuery("select l from LabResult l where l.test = 'HBA1C' and l.takenOn >= :from and l.takenOn <= :to", LabResult.class)
                .setParameter("from", firstDay).setParameter("to", lastDay).getResultList().stream()
                .filter(l -> followedIds.contains(l.getPatientId())).toList();
        Map<UUID, LabResult> latest = new LinkedHashMap<>();
        labs.stream().sorted(Comparator.comparing(LabResult::getTakenOn)).forEach(l -> latest.put(l.getPatientId(), l));
        long inTarget = latest.values().stream().filter(l -> l.getValue() <= 7.0).count();
        List<Line> clinical = new ArrayList<>();
        clinical.add(new Line("hba1cInTarget", "Patients whose latest HbA1c this month was 7.0% or lower", inTarget, (long) latest.size(),
                latest.isEmpty() ? "No HbA1c results were entered this month." : "Measured against the common 7.0% target, not each patient's own."));
        boolean inProgress = !end.equals(to);
        if (inProgress) {
            Map<String, Long> direction = new LinkedHashMap<>();
            for (Patient p : followed) {
                direction.merge(trends.summary(p.getId(), 30).overall(), 1L, Long::sum);
            }
            clinical.add(new Line("improving", "Readings and doses improving over the last 30 days", direction.getOrDefault(Trends.IMPROVING, 0L), (long) followed.size(), null));
            clinical.add(new Line("worsening", "Readings or doses worsening over the last 30 days", direction.getOrDefault(Trends.WORSENING, 0L), (long) followed.size(),
                    "Each one is on the risk score and the call list."));
        }
        sections.add(new Section("Clinical picture", clinical));

        return new Report(actor.getClinic().getName(), ym.toString(), firstDay, lastDay, inProgress, sections, clock.instant());
    }

    private <T> List<T> list(String jpql, Class<T> type, UUID clinicId, Instant from, Instant to) {
        return em.createQuery(jpql, type).setParameter("clinic", clinicId).setParameter("from", from).setParameter("to", to).getResultList();
    }
}
