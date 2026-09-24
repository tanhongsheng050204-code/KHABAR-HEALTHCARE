package com.khabar.api.clinicops;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/** Settings with defaults, today's cover, and the clinic activity log, for the rest of the API. */
@Service
public class ClinicOps {

    private final ClinicSettingsRepository settings;
    private final RotaEntryRepository rota;
    private final ClinicActivityRepository activity;
    private final AppUserRepository users;
    private final AdjustableClock clock;

    public ClinicOps(ClinicSettingsRepository settings, RotaEntryRepository rota,
                     ClinicActivityRepository activity, AppUserRepository users, AdjustableClock clock) {
        this.settings = settings;
        this.rota = rota;
        this.activity = activity;
        this.users = users;
        this.clock = clock;
    }

    /** The clinic's saved settings, or the defaults if it has not saved any. Never saved by this call. */
    public ClinicSettings settingsFor(UUID clinicId) {
        return settings.findById(clinicId).orElseGet(() -> new ClinicSettings(clinicId));
    }

    public record Coverage(DayOfWeek day, String onDuty, String backup, String escalationContact, String warning) {
    }

    /** Who owns follow-up today. Says so plainly when nobody does, instead of implying constant cover. */
    public Coverage coverageToday(UUID clinicId) {
        DayOfWeek day = LocalDate.now(clock).getDayOfWeek();
        String contact = settingsFor(clinicId).getEscalationContact();
        return rota.findByClinicIdAndDayOfWeek(clinicId, day)
                .map(entry -> new Coverage(day, nameOf(entry.getPrimaryUserId()), nameOf(entry.getBackupUserId()), contact,
                        entry.getBackupUserId() == null ? "No backup is rostered today." : null))
                .orElse(new Coverage(day, null, null, contact,
                        "Nobody is rostered for follow-up today. Urgent replies still get the 999 advice, but no one is assigned to call."));
    }

    public void record(AppUser actor, ClinicActivity.Action action, String subject) {
        activity.save(new ClinicActivity(actor.getClinic().getId(), actor.getId(), actor.getDisplayName(), action, subject, clock.instant()));
    }

    /** A short, non-identifying reference for a case in the activity log. */
    public static String caseRef(UUID caseId) {
        return "Case " + caseId.toString().substring(0, 8);
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : users.findById(userId).map(AppUser::getDisplayName).orElse("Former staff member");
    }
}
