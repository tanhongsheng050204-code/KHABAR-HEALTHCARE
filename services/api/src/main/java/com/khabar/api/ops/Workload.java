package com.khabar.api.ops;

import com.khabar.api.clinicops.ClinicOps;
import com.khabar.api.clinicops.ClinicSettings;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.FollowUpCase;
import com.khabar.api.followup.FollowUpCases;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffGrant;
import com.khabar.api.identity.ClinicStaffGrantRepository;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.CurrentUser;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who holds which follow-up work: open cases per doctor and nurse, how many are urgent or past the clinic's
 * acknowledgement time, and what each closed this week. "Balance" gives each unowned open case to whoever holds
 * the fewest, most urgent first, and logs every assignment like a manual one. Counts and staff names only.
 */
@RestController
public class Workload {

    public record Person(UUID userId, String name, String role, int open, int urgent, int overdue, int closedThisWeek, int callsThisWeek) {
    }

    public record Board(List<Person> staff, int unowned, int unownedUrgent, Instant at) {
    }

    public record Balanced(int assigned, Board board) {
    }

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final ClinicStaffGrantRepository grants;
    private final FollowUpCases cases;
    private final ClinicOps clinicOps;
    private final EntityManager em;
    private final AdjustableClock clock;

    public Workload(CurrentUser currentUser, ClinicStaffAccess staffAccess, ClinicStaffGrantRepository grants, FollowUpCases cases,
                    ClinicOps clinicOps, EntityManager em, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.grants = grants;
        this.cases = cases;
        this.clinicOps = clinicOps;
        this.em = em;
        this.clock = clock;
    }

    @GetMapping("/api/clinic/workload")
    @Transactional(readOnly = true)
    public Board board(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !(staffAccess.canManageFollowUp(actor) || staffAccess.canManageStaff(actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The workload board is for clinic staff.");
        }
        return build(actor.getClinic().getId());
    }

    @PostMapping("/api/clinic/workload/balance")
    @Transactional
    public Balanced balance(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !staffAccess.canManageStaff(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors and clinic administrators balance the workload.");
        }
        UUID clinicId = actor.getClinic().getId();
        Map<UUID, Integer> load = new LinkedHashMap<>();
        followUpStaff(clinicId).forEach(u -> load.put(u.getId(), 0));
        if (load.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No doctor or nurse can take follow-up cases yet.");
        }
        List<FollowUpCase> open = cases.openCases(clinicId);
        open.stream().filter(c -> c.getOwnerId() != null && load.containsKey(c.getOwnerId()))
                .forEach(c -> load.merge(c.getOwnerId(), 1, Integer::sum));
        List<FollowUpCase> unowned = open.stream().filter(c -> c.getOwnerId() == null)
                .sorted(Comparator.comparing(FollowUpCase::getLevel).thenComparing(FollowUpCase::getOpenedAt)).toList();
        int assigned = 0;
        for (FollowUpCase c : unowned) {
            UUID least = load.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow().getKey();
            cases.assign(actor, c, least);
            load.merge(least, 1, Integer::sum);
            assigned++;
        }
        return new Balanced(assigned, build(clinicId));
    }

    private List<AppUser> followUpStaff(UUID clinicId) {
        Map<UUID, AppUser> people = new LinkedHashMap<>();
        for (ClinicStaffGrant g : grants.findByClinicIdAndRevokedAtIsNullOrderByGrantedAt(clinicId)) {
            if (g.getRole() == ClinicStaffRole.DOCTOR || g.getRole() == ClinicStaffRole.NURSE) {
                people.putIfAbsent(g.getAppUser().getId(), g.getAppUser());
            }
        }
        return new ArrayList<>(people.values());
    }

    Board build(UUID clinicId) {
        Instant now = clock.instant();
        Instant weekAgo = now.minus(Duration.ofDays(7));
        ClinicSettings settings = clinicOps.settingsFor(clinicId);
        List<FollowUpCase> open = cases.openCases(clinicId);
        List<FollowUpCase> closed = em.createQuery("select c from FollowUpCase c where c.clinic.id = :clinic and c.closedAt >= :from", FollowUpCase.class)
                .setParameter("clinic", clinicId).setParameter("from", weekAgo).getResultList();
        List<Person> people = new ArrayList<>();
        for (AppUser u : followUpStaff(clinicId)) {
            List<FollowUpCase> mine = open.stream().filter(c -> u.getId().equals(c.getOwnerId())).toList();
            int overdue = (int) mine.stream().filter(c -> c.getAcknowledgedAt() == null
                    && now.isAfter(c.getOpenedAt().plus(Duration.ofMinutes(settings.ackMinutes(c.getLevel()))))).count();
            int closedMine = (int) closed.stream().filter(c -> u.getId().equals(c.getClosedBy())).count();
            int calls = closed.stream().filter(c -> u.getId().equals(c.getClosedBy())).mapToInt(FollowUpCase::getContactAttempts).sum()
                    + mine.stream().filter(c -> c.getLastAttemptAt() != null && c.getLastAttemptAt().isAfter(weekAgo)).mapToInt(FollowUpCase::getContactAttempts).sum();
            people.add(new Person(u.getId(), u.getDisplayName(), staffAccess.rolesFor(u).stream().map(Enum::name).findFirst().orElse(u.getRole().name()),
                    mine.size(), (int) mine.stream().filter(c -> c.getLevel() == TriageLevel.RED).count(), overdue, closedMine, calls));
        }
        people.sort(Comparator.comparingInt(Person::open).reversed().thenComparing(Person::name));
        List<FollowUpCase> unowned = open.stream().filter(c -> c.getOwnerId() == null).toList();
        return new Board(people, unowned.size(), (int) unowned.stream().filter(c -> c.getLevel() == TriageLevel.RED).count(), now);
    }
}
