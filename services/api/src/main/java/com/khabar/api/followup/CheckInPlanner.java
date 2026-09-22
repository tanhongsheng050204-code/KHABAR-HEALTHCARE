package com.khabar.api.followup;

import com.khabar.api.patients.Patient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Starts a patient's 30-day follow-up: check-ins on day 1, 3, 7, 14 and 30 after the visit. */
@Service
public class CheckInPlanner {

    public static final List<Integer> DAYS = List.of(1, 3, 7, 14, 30);

    private final CheckInRepository checkIns;

    public CheckInPlanner(CheckInRepository checkIns) {
        this.checkIns = checkIns;
    }

    /** Any check-ins still waiting from an earlier visit are replaced by the new plan. */
    public List<CheckIn> startFollowUp(Patient patient, UUID encounterId, LocalDate visitDay, boolean fasting) {
        checkIns.deleteAll(checkIns.findByPatientIdAndStatus(patient.getId(), CheckIn.Status.PENDING));
        patient.startFollowUp(visitDay);
        return checkIns.saveAll(DAYS.stream().map(day -> new CheckIn(patient, encounterId, visitDay, day, fasting)).toList());
    }
}
