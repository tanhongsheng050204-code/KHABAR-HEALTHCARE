package com.khabar.api.labs;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientGate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Laboratory results the doctor enters, each judged against its target: HbA1c against the patient's pathway target
 * (7.0% by default), LDL at most 2.6 mmol/L, eGFR at least 60. Creatinine is recorded without a target.
 */
@RestController
public class Labs {

    /** unit, plausible range, and whether higher is worse. */
    record Test(String code, String label, String unit, double min, double max, boolean higherIsWorse, Double defaultTarget) {
    }

    static final Map<String, Test> TESTS = tests();

    private static Map<String, Test> tests() {
        Map<String, Test> all = new LinkedHashMap<>();
        all.put("HBA1C", new Test("HBA1C", "HbA1c", "%", 3, 20, true, 7.0));
        all.put("LDL", new Test("LDL", "LDL cholesterol", "mmol/L", 0.3, 15, true, 2.6));
        all.put("EGFR", new Test("EGFR", "eGFR", "mL/min/1.73m²", 2, 150, false, 60.0));
        all.put("CREATININE", new Test("CREATININE", "Creatinine", "µmol/L", 20, 2000, true, null));
        return all;
    }

    public record Input(String test, Double value, LocalDate takenOn) {
    }

    /** status is IN_TARGET, OUT_OF_TARGET or NO_TARGET. */
    public record LabView(UUID id, String test, String label, String unit, double value, LocalDate takenOn, Double target,
                          String status) {
    }

    private final CurrentUser currentUser;
    private final PatientGate gate;
    private final LabResultRepository results;
    private final Pathways pathways;
    private final AdjustableClock clock;

    public Labs(CurrentUser currentUser, PatientGate gate, LabResultRepository results, Pathways pathways, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.gate = gate;
        this.results = results;
        this.pathways = pathways;
        this.clock = clock;
    }

    @GetMapping("/api/patients/{id}/labs")
    @Transactional
    public List<LabView> list(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = gate.read(currentUser.from(jwt), id);
        return views(patient.getId());
    }

    @PostMapping("/api/patients/{id}/labs")
    @Transactional
    public LabView add(@PathVariable UUID id, @RequestBody Input input, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = currentUser.from(jwt);
        Patient patient = gate.doctorAt(doctor, id);
        Test test = input.test() == null ? null : TESTS.get(input.test().trim().toUpperCase(java.util.Locale.ROOT));
        if (test == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose HBA1C, LDL, EGFR or CREATININE.");
        }
        if (input.value() == null || input.value() < test.min() || input.value() > test.max()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, test.label() + " must be between " + test.min() + " and " + test.max() + " " + test.unit() + ".");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate taken = input.takenOn() == null ? today : input.takenOn();
        if (taken.isAfter(today) || taken.isBefore(today.minusYears(5))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The test date must be within the last five years.");
        }
        LabResult saved = results.save(new LabResult(patient.getId(), test.code(), input.value(), taken, doctor.getId(), clock.instant()));
        return view(saved, target(test, pathways.targets(patient.getId())));
    }

    public List<LabView> views(UUID patientId) {
        Pathways.Targets targets = pathways.targets(patientId);
        return results.findByPatientIdOrderByTakenOnAscRecordedAtAsc(patientId).stream()
                .map(r -> view(r, target(TESTS.get(r.getTest()), targets)))
                .toList();
    }

    private static Double target(Test test, Pathways.Targets targets) {
        if (test == null) {
            return null;
        }
        if (test.code().equals("HBA1C") && targets.hba1cMax() != null) {
            return targets.hba1cMax();
        }
        return test.defaultTarget();
    }

    private static LabView view(LabResult r, Double target) {
        Test test = TESTS.get(r.getTest());
        String status = target == null || test == null ? "NO_TARGET"
                : (test.higherIsWorse() ? r.getValue() <= target : r.getValue() >= target) ? "IN_TARGET" : "OUT_OF_TARGET";
        return new LabView(r.getId(), r.getTest(), test == null ? r.getTest() : test.label(), test == null ? "" : test.unit(),
                r.getValue(), r.getTakenOn(), target, status);
    }
}
