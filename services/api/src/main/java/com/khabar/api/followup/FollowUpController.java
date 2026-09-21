package com.khabar.api.followup;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.Redactor;
import com.khabar.api.service.AgentClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/**
 * Receives a patient's follow-up reply (today from the app, later from the WhatsApp webhook),
 * triages it and stores it. A reply is never lost: if triage fails, it goes to a person.
 */
@RestController
@RequestMapping("/api/followup")
public class FollowUpController {

    private static final Logger log = LoggerFactory.getLogger(FollowUpController.class);

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientReplyRepository replies;
    private final AgentClientService agents;

    public FollowUpController(CurrentUser currentUser, PatientRepository patients, PatientReplyRepository replies, AgentClientService agents) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.replies = replies;
        this.agents = agents;
    }

    public record ReplyRequest(String text) {
    }

    public record ReplyResponse(TriageLevel level) {
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

        TriageLevel level;
        String matched = null;
        try {
            Map<String, Object> result = agents.triageReply(Redactor.redact(request.text(), patient));
            level = TriageLevel.fromAgent(result == null ? null : result.get("level"));
            matched = result == null || result.get("matched") == null ? null : result.get("matched").toString();
        } catch (RuntimeException e) {
            log.warn("Triage unavailable, sending reply to a person: {}", e.getMessage());
            level = TriageLevel.REVIEW;
        }
        replies.save(new PatientReply(patient, request.text(), Instant.now(), level, matched));
        return new ReplyResponse(level);
    }
}
