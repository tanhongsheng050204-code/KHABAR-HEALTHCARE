package com.khabar.api.clinicops;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.encounters.Encounter;
import com.khabar.api.followup.CheckIn;
import com.khabar.api.followup.ClosureReason;
import com.khabar.api.followup.FollowUpCase;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "What follow-up did for this clinic": counts from the clinic's own records over a recent window, each with
 * what it is counted out of. Nothing here is an estimate or a benchmark; a count with nothing behind it says so.
 */
@RestController
public class ImpactController {

    static final Set<ClosureReason> AFTER_CONTACT = Set.of(ClosureReason.CONTACTED_NO_FURTHER_ACTION, ClosureReason.ADVICE_GIVEN_PER_PROTOCOL,
            ClosureReason.APPOINTMENT_ARRANGED, ClosureReason.DOCTOR_REVIEWED, ClosureReason.REFERRED_TO_EMERGENCY_CARE,
            ClosureReason.CONTACT_RECORDED);

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final ClinicOps clinicOps;
    private final EntityManager em;
    private final AdjustableClock clock;

    public ImpactController(CurrentUser currentUser, ClinicStaffAccess staffAccess, ClinicOps clinicOps, EntityManager em, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.clinicOps = clinicOps;
        this.em = em;
        this.clock = clock;
    }

    /** value out of `of` (null when it is a plain count). */
    public record Metric(String key, String label, long value, Long of, String note) {
    }

    public record Impact(int days, Instant from, Instant to, List<Metric> metrics, Long medianAckMinutes) {
    }

    @GetMapping("/api/clinic/impact")
    @Transactional(readOnly = true)
    public Impact impact(@RequestParam(defaultValue = "30") int days, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !(staffAccess.canManageFollowUp(actor) || staffAccess.canManageStaff(actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The impact summary is for clinic staff.");
        }
        if (days < 1 || days > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between 1 and 365 days.");
        }
        UUID clinicId = actor.getClinic().getId();
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(days));
        ClinicSettings settings = clinicOps.settingsFor(clinicId);
        List<Metric> metrics = new ArrayList<>();

        List<FollowUpCase> opened = em.createQuery(
                        "select c from FollowUpCase c where c.clinic.id = :clinic and c.openedAt >= :from and c.openedAt < :to", FollowUpCase.class)
                .setParameter("clinic", clinicId).setParameter("from", from).setParameter("to", to).getResultList();
        List<Long> ackMinutes = new ArrayList<>();
        long onTime = 0;
        for (FollowUpCase c : opened) {
            if (c.getAcknowledgedAt() != null) {
                long minutes = Duration.between(c.getOpenedAt(), c.getAcknowledgedAt()).toMinutes();
                ackMinutes.add(minutes);
                if (minutes <= settings.ackMinutes(c.getLevel())) {
                    onTime++;
                }
            }
        }
        metrics.add(new Metric("ackOnTime", "Cases acknowledged within the clinic's own time limit", onTime, (long) opened.size(),
                opened.isEmpty() ? "No follow-up cases were opened in this period." : null));

        List<FollowUpCase> closed = em.createQuery(
                        "select c from FollowUpCase c where c.clinic.id = :clinic and c.closedAt >= :from and c.closedAt < :to", FollowUpCase.class)
                .setParameter("clinic", clinicId).setParameter("from", from).setParameter("to", to).getResultList();
        long afterContact = closed.stream().filter(c -> AFTER_CONTACT.contains(c.getClosureReason())).count();
        metrics.add(new Metric("closedAfterContact", "Cases closed after the patient was reached or reviewed", afterContact, (long) closed.size(),
                closed.isEmpty() ? "No cases were closed in this period." : null));

        long urgent = count("select count(r) from PatientReply r where r.patient.clinic.id = :clinic and r.receivedAt >= :from and r.receivedAt < :to and r.level = :level",
                clinicId, from, to, TriageLevel.RED);
        metrics.add(new Metric("urgentReplies", "Replies that got the 999 advice and went to the top of the call list", urgent, null, null));

        List<CheckIn> sent = em.createQuery(
                        "select c from CheckIn c where c.patient.clinic.id = :clinic and c.sentAt >= :from and c.sentAt < :to", CheckIn.class)
                .setParameter("clinic", clinicId).setParameter("from", from).setParameter("to", to).getResultList();
        long answered = sent.stream().filter(c -> c.getStatus() == CheckIn.Status.ANSWERED).count();
        metrics.add(new Metric("checkInsAnswered", "Check-ins answered", answered, (long) sent.size(),
                sent.isEmpty() ? "No check-ins were sent in this period." : null));

        List<Encounter> visits = em.createQuery(
                        "select e from Encounter e where e.patient.clinic.id = :clinic and e.finalisedAt >= :from and e.finalisedAt < :to", Encounter.class)
                .setParameter("clinic", clinicId).setParameter("from", from).setParameter("to", to).getResultList();
        long duplicates = visits.stream().filter(e -> e.getFindings().stream().anyMatch(f -> f.getCheckName().toLowerCase().contains("duplicate"))).count();
        metrics.add(new Metric("duplicatesCaught", "Finalised visits where the safety check flagged a duplicate medicine", duplicates, (long) visits.size(),
                visits.isEmpty() ? "No visits were finalised in this period." : null));

        long held = count("select count(s) from VisitSummary s where s.patient.clinic.id = :clinic and s.createdAt >= :from and s.createdAt < :to and s.heldReason is not null",
                clinicId, from, to, null);
        metrics.add(new Metric("plansHeld", "Take-home plans held by the plan check for a doctor to look at", held, (long) visits.size(), null));

        Long median = null;
        if (!ackMinutes.isEmpty()) {
            List<Long> sorted = ackMinutes.stream().sorted().toList();
            median = sorted.get(sorted.size() / 2);
        }
        return new Impact(days, from, to, metrics, median);
    }

    private long count(String jpql, UUID clinicId, Instant from, Instant to, TriageLevel level) {
        var query = em.createQuery(jpql, Long.class).setParameter("clinic", clinicId).setParameter("from", from).setParameter("to", to);
        if (level != null) {
            query.setParameter("level", level);
        }
        return query.getSingleResult();
    }
}
