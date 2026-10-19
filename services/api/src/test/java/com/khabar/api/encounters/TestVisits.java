package com.khabar.api.encounters;

import com.khabar.api.identity.AppUser;
import com.khabar.api.patients.Patient;
import com.khabar.api.service.AgentDtos.DraftedReport;
import com.khabar.api.service.AgentDtos.DraftedRx;

import java.time.Instant;
import java.util.List;

/** Signed visits for tests outside this package, without going through the drafting and safety-check flow. */
public final class TestVisits {

    private TestVisits() {
    }

    public static Encounter signed(Patient patient, AppUser doctor, Instant at, boolean fasting, Double followUpWeeks, List<DraftedRx> rx) {
        Encounter visit = new Encounter(patient, doctor, at);
        visit.applyDraft("notes", new DraftedReport("Type 2 diabetes", "Continue medicines", "Review", followUpWeeks, List.of(), rx), fasting);
        visit.recordFindings(List.of());
        visit.finalise(at);
        return visit;
    }

    public static DraftedRx rx(String name, double mg, List<String> times, String timing, boolean asNeeded) {
        return new DraftedRx(name + " " + (int) mg + "mg", name, mg, 1.0, times.size(), times, timing, asNeeded);
    }
}
