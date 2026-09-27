package com.khabar.api.clinicops;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.graph.PatientGraph;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.messaging.Messenger;
import com.khabar.api.service.AgentClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Clinic settings, the follow-up rota, the activity log and integration health. Doctors and clinic
 * administrators manage them; nurses can read the settings and rota. None of these ever returns a patient.
 */
@RestController
@RequestMapping("/api/clinic")
public class ClinicOpsController {

    static final int MIN_MINUTES = 5;
    static final int MAX_MINUTES = 7 * 24 * 60;

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final ClinicSettingsRepository settings;
    private final RotaEntryRepository rota;
    private final ClinicActivityRepository activity;
    private final ClinicOps clinicOps;
    private final AppUserRepository users;
    private final AgentClientService agents;
    private final Messenger messenger;
    private final PatientGraph graph;
    private final boolean schedulerEnabled;
    private final boolean realSignIn;
    private final AdjustableClock clock;

    public ClinicOpsController(CurrentUser currentUser, ClinicStaffAccess staffAccess, ClinicSettingsRepository settings,
                               RotaEntryRepository rota, ClinicActivityRepository activity, ClinicOps clinicOps,
                               AppUserRepository users, AgentClientService agents, Messenger messenger, PatientGraph graph,
                               @Value("${khabar.checkins.scheduler-enabled:true}") boolean schedulerEnabled,
                               @Value("${khabar.security.supabase-jwks-url:}") String jwksUrl,
                               AdjustableClock clock) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.settings = settings;
        this.rota = rota;
        this.activity = activity;
        this.clinicOps = clinicOps;
        this.users = users;
        this.agents = agents;
        this.messenger = messenger;
        this.graph = graph;
        this.schedulerEnabled = schedulerEnabled;
        this.realSignIn = !jwksUrl.isBlank();
        this.clock = clock;
    }

    public record RotaDay(DayOfWeek day, UUID primaryUserId, String primaryName, UUID backupUserId, String backupName) {
    }

    public record SettingsView(String hours, String escalationContact, int redAckMinutes, int watchAckMinutes,
                               int reviewAckMinutes, List<RotaDay> rota, Instant updatedAt, ClinicOps.Coverage today) {
    }

    public record RotaInput(DayOfWeek day, UUID primaryUserId, UUID backupUserId) {
    }

    public record SettingsInput(String hours, String escalationContact, Integer redAckMinutes, Integer watchAckMinutes,
                                Integer reviewAckMinutes, List<RotaInput> rota) {
    }

    public record ActivityView(String by, ClinicActivity.Action action, String subject, Instant at) {
    }

    public record Integration(String name, String status, String detail) {
    }

    @GetMapping("/settings")
    @Transactional(readOnly = true)
    public SettingsView settings(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !(staffAccess.canManageStaff(actor) || staffAccess.canManageFollowUp(actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clinic settings are for clinic staff.");
        }
        return view(actor.getClinic().getId());
    }

    @PutMapping("/settings")
    @Transactional
    public SettingsView update(@RequestBody SettingsInput input, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        UUID clinicId = actor.getClinic().getId();
        if (input == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send the clinic settings.");
        }
        int red = minutes(input.redAckMinutes(), "urgent");
        int watch = minutes(input.watchAckMinutes(), "watch");
        int review = minutes(input.reviewAckMinutes(), "review");
        if (red > watch || watch > review) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Urgent cases must be acknowledged no later than watch cases, and watch cases no later than review cases.");
        }
        List<RotaInput> days = input.rota() == null ? List.of() : input.rota();
        Set<DayOfWeek> seen = new HashSet<>();
        for (RotaInput day : days) {
            if (day == null || day.day() == null || !seen.add(day.day())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each day can appear once in the rota.");
            }
            requireFollowUpStaff(day.primaryUserId(), clinicId, "on duty");
            if (day.backupUserId() != null) {
                requireFollowUpStaff(day.backupUserId(), clinicId, "the backup");
                if (day.backupUserId().equals(day.primaryUserId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The backup must be a different person.");
                }
            }
        }
        ClinicSettings saved = settings.findById(clinicId).orElseGet(() -> new ClinicSettings(clinicId));
        saved.update(text(input.hours()), text(input.escalationContact()), red, watch, review, actor.getId(), clock.instant());
        settings.save(saved);
        rota.deleteByClinicId(clinicId);
        rota.flush();
        days.forEach(day -> rota.save(new RotaEntry(clinicId, day.day(), day.primaryUserId(), day.backupUserId())));
        clinicOps.record(actor, ClinicActivity.Action.SETTINGS_UPDATED, "Clinic settings and rota");
        return view(clinicId);
    }

    /** What staff did in this clinic, newest first. Cases appear by reference, never by patient. */
    @GetMapping("/activity")
    @Transactional(readOnly = true)
    public List<ActivityView> activity(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        return activity.findByClinicIdOrderByIdDesc(actor.getClinic().getId(), PageRequest.of(0, 200)).stream()
                .map(a -> new ActivityView(a.getActorLabel(), a.getAction(), a.getSubject(), a.getAt()))
                .toList();
    }

    /** Whether each outside service is working, so a gap is visible instead of silently assumed away. */
    @GetMapping("/integrations")
    public List<Integration> integrations(@AuthenticationPrincipal Jwt jwt) {
        requireManager(jwt);
        Map<String, Object> health = agents.checkAgentHealth();
        if (health == null) {
            health = Map.of();
        }
        boolean agentsUp = "ok".equals(String.valueOf(health.get("status"))) || "healthy".equals(String.valueOf(health.get("status")));
        boolean whatsapp = "whatsapp".equalsIgnoreCase(messenger.channel());
        return List.of(
                new Integration("AI agents", agentsUp ? "OK" : "DOWN",
                        agentsUp ? "Drafting, safety checks and triage are available." : "Drafts and safety checks are unavailable; replies get the 999 advice and wait for a person."),
                new Integration("Patient messages", whatsapp ? "OK" : "LIMITED",
                        whatsapp ? "Sent through WhatsApp." : "Kept in the local outbox; nothing reaches patients' phones."),
                new Integration("Check-in scheduler", schedulerEnabled ? "OK" : "OFF",
                        schedulerEnabled ? "Check-ins go out on their due days." : "No check-ins are sent until it is turned on."),
                new Integration("Patient graph", graph.enabled() ? "OK" : "OFF",
                        graph.enabled() ? "Context is written and read by random ID." : "Not configured; checks use the main database only."),
                new Integration("Real sign-in", realSignIn ? "OK" : "OFF",
                        realSignIn ? "Supabase sign-ins are verified with the project's published keys." : "Only demo or shared-secret tokens are accepted."));
    }

    private SettingsView view(UUID clinicId) {
        ClinicSettings s = clinicOps.settingsFor(clinicId);
        List<RotaDay> days = rota.findByClinicIdOrderByDayOfWeek(clinicId).stream()
                .map(r -> new RotaDay(r.getDayOfWeek(), r.getPrimaryUserId(), nameOf(r.getPrimaryUserId()),
                        r.getBackupUserId(), nameOf(r.getBackupUserId())))
                .toList();
        return new SettingsView(s.getHours(), s.getEscalationContact(), s.getRedAckMinutes(), s.getWatchAckMinutes(),
                s.getReviewAckMinutes(), days, s.getUpdatedAt(), clinicOps.coverageToday(clinicId));
    }

    private void requireFollowUpStaff(UUID userId, UUID clinicId, String what) {
        users.findById(userId == null ? new UUID(0, 0) : userId)
                .filter(u -> u.getClinic() != null && u.getClinic().getId().equals(clinicId) && staffAccess.canManageFollowUp(u))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The person " + what + " must be a doctor or nurse at this clinic."));
    }

    private static int minutes(Integer value, String level) {
        if (value == null || value < MIN_MINUTES || value > MAX_MINUTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Set the " + level + " acknowledgement time between " + MIN_MINUTES + " minutes and 7 days.");
        }
        return value;
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : users.findById(userId).map(AppUser::getDisplayName).orElse("Former staff member");
    }

    private AppUser requireManager(Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !staffAccess.canManageStaff(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clinic doctors or administrators can manage clinic settings.");
        }
        return actor;
    }
}
