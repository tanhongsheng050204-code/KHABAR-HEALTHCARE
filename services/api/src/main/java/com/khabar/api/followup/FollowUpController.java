package com.khabar.api.followup;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** A follow-up reply sent from the Khabar app. WhatsApp replies arrive through the webhook instead. */
@RestController
@RequestMapping("/api/followup")
public class FollowUpController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final FollowUpService followUp;

    public FollowUpController(CurrentUser currentUser, PatientRepository patients, FollowUpService followUp) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.followUp = followUp;
    }

    public record ReplyRequest(String text) {
    }

    public record ReplyResponse(TriageLevel level, String answer) {
    }

    @PostMapping("/replies")
    public ReplyResponse reply(@RequestBody ReplyRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only patients send follow-up replies.");
        }
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply text is empty.");
        }
        Patient patient = patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));
        FollowUpService.Outcome outcome = followUp.receiveReply(patient, request.text());
        return new ReplyResponse(outcome.level(), outcome.answer());
    }
}
