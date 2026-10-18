package com.khabar.api.followup;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** "Call these patients today": the doctor's clinic, most urgent unhandled replies first, then patients who went quiet. */
@RestController
@RequestMapping("/api/clinic/call-list")
public class CallListController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final AdjustableClock clock;
    private final ClinicStaffAccess staffAccess;
    private final FollowUpCases cases;
    private final CallLists callLists;

    public CallListController(CurrentUser currentUser, PatientRepository patients, AdjustableClock clock,
                              ClinicStaffAccess staffAccess, FollowUpCases cases, CallLists callLists) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.clock = clock;
        this.staffAccess = staffAccess;
        this.cases = cases;
        this.callLists = callLists;
    }

    public record ContactRequest(Instant observedThrough) {
    }

    /**
     * Listing a patient opens their follow-up case if they have none. A case stays on the list until someone
     * closes it, even when what put the patient there has since cleared.
     */
    @GetMapping
    @Transactional
    public CallLists.CallList callList(@AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireFollowUpStaff(jwt);
        return callLists.build(doctor.getClinic().getId(), clock.instant(), true);
    }

    @PostMapping("/{patientId}/called")
    @Transactional
    public Map<String, Object> markCalled(@PathVariable UUID patientId, @RequestBody ContactRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireFollowUpStaff(jwt);
        if (request == null || request.observedThrough() == null || request.observedThrough().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh the call list before recording contact.");
        }
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!patient.getClinic().getId().equals(doctor.getClinic().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        FollowUpCases.Resolved resolved = cases.resolveSources(doctor, patientId, request.observedThrough(), clock.instant());
        cases.recordContact(doctor, patient, request.observedThrough());
        return Map.of("handledReplies", resolved.replies(), "handledReadings", resolved.readings(), "handledCheckIns", resolved.checkIns());
    }

    private AppUser requireFollowUpStaff(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (!staffAccess.canManageFollowUp(user) || user.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The call list is for clinic doctors and nurses.");
        }
        return user;
    }
}
