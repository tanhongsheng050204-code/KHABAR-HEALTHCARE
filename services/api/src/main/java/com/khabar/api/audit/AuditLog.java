package com.khabar.api.audit;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLog {

    private final AuditEntryRepository entries;
    private final AdjustableClock clock;

    public AuditLog(AuditEntryRepository entries, AdjustableClock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    public void record(AppUser actor, UUID patientId, AuditAction action) {
        entries.save(new AuditEntry(patientId, actor.getId(), labelFor(actor), action, clock.instant()));
    }

    /** Newest first. */
    public List<AuditEntry> forPatient(UUID patientId) {
        return entries.findByPatientIdOrderByIdDesc(patientId);
    }

    private static String labelFor(AppUser actor) {
        return switch (actor.getRole()) {
            case DOCTOR -> actor.getDisplayName() + (actor.getClinic() != null ? " · " + actor.getClinic().getName() : "");
            case NURSE -> actor.getDisplayName() + " · nurse";
            case CLINIC_ADMIN -> actor.getDisplayName() + " · clinic admin";
            case CAREGIVER -> actor.getDisplayName() + " · caregiver";
            case PATIENT -> actor.getDisplayName();
        };
    }
}
