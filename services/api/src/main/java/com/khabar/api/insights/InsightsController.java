package com.khabar.api.insights;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientGate;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.pathways.PatientPathwayRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Trends for a patient's record, and the follow-up risk score for the clinic's staff. */
@RestController
public class InsightsController {

    private final CurrentUser currentUser;
    private final PatientGate gate;
    private final ClinicStaffAccess staffAccess;
    private final PatientRepository patients;
    private final PatientPathwayRepository pathways;
    private final Trends trends;
    private final RiskScores risk;

    public InsightsController(CurrentUser currentUser, PatientGate gate, ClinicStaffAccess staffAccess, PatientRepository patients,
                              PatientPathwayRepository pathways, Trends trends, RiskScores risk) {
        this.currentUser = currentUser;
        this.gate = gate;
        this.staffAccess = staffAccess;
        this.patients = patients;
        this.pathways = pathways;
        this.trends = trends;
        this.risk = risk;
    }

    public record RiskRow(UUID patientId, String fullName, String preferredLanguage, int score, List<RiskScores.Reason> reasons) {
    }

    @GetMapping("/api/patients/{id}/trends")
    @Transactional
    public Trends.Summary trends(@PathVariable UUID id, @RequestParam(defaultValue = "30") int days, @AuthenticationPrincipal Jwt jwt) {
        if (days < 7 || days > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between 7 and 180 days.");
        }
        Patient patient = gate.read(currentUser.from(jwt), id);
        return trends.summary(patient.getId(), days);
    }

    /** The score is for the clinic's follow-up staff, not shown to patients or caregivers. */
    @GetMapping("/api/patients/{id}/risk")
    @Transactional(readOnly = true)
    public RiskScores.Score patientRisk(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser staff = followUpStaff(jwt);
        Patient patient = patients.findById(id).filter(p -> p.getClinic().getId().equals(staff.getClinic().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return risk.score(patient.getId(), staff.getClinic().getId());
    }

    /** Every patient the clinic follows (in follow-up or on a pathway), highest score first. */
    @GetMapping("/api/clinic/risk")
    @Transactional(readOnly = true)
    public List<RiskRow> clinicRisk(@AuthenticationPrincipal Jwt jwt) {
        AppUser staff = followUpStaff(jwt);
        UUID clinicId = staff.getClinic().getId();
        Set<UUID> onPathway = pathways.findByClinicIdAndEndedAtIsNull(clinicId).stream().map(p -> p.getPatientId()).collect(Collectors.toSet());
        Map<String, Integer> points = risk.pointsFor(clinicId);
        return patients.findByClinicId(clinicId).stream()
                .filter(p -> p.getFollowUpStart() != null || onPathway.contains(p.getId()))
                .map(p -> {
                    RiskScores.Score s = risk.score(p.getId(), points);
                    return new RiskRow(p.getId(), p.getFullName(), p.getPreferredLanguage(), s.score(), s.reasons());
                })
                .sorted(Comparator.comparingInt(RiskRow::score).reversed().thenComparing(RiskRow::fullName))
                .toList();
    }

    @GetMapping("/api/clinic/risk-weights")
    @Transactional(readOnly = true)
    public List<RiskScores.Weight> weights(@AuthenticationPrincipal Jwt jwt) {
        return risk.weights(followUpStaff(jwt).getClinic().getId());
    }

    @PutMapping("/api/clinic/risk-weights")
    @Transactional
    public List<RiskScores.Weight> updateWeights(@RequestBody Map<String, Integer> changes, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !staffAccess.canManageStaff(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors and clinic administrators set the risk points.");
        }
        return risk.updateWeights(actor.getClinic().getId(), changes, actor.getId());
    }

    private AppUser followUpStaff(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getClinic() == null || !staffAccess.canManageFollowUp(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The risk score is for the clinic's follow-up staff.");
        }
        return user;
    }
}
