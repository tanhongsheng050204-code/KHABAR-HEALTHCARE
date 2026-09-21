package com.khabar.api.intake;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.Redactor;
import com.khabar.api.service.AgentClientService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * The patient's pre-visit intake chat. The patient is identified by their sign-in, never by
 * anything in the request body, and the AI service only ever receives their graphId.
 */
@RestController
@RequestMapping("/api/intake")
public class IntakeController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final AgentClientService agents;

    public IntakeController(CurrentUser currentUser, PatientRepository patients, AgentClientService agents) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.agents = agents;
    }

    public record ChatMessage(String role, String content) {
    }

    public record IntakeRequest(List<ChatMessage> messages) {
    }

    public record IntakeResponse(String nextQuestion, boolean complete) {
    }

    @PostMapping("/chat")
    public IntakeResponse chat(@RequestBody IntakeRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Intake is filled in by the patient.");
        }
        Patient patient = patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));

        List<Map<String, String>> history = request.messages() == null ? List.of() : request.messages().stream()
                .map(m -> Map.of("role", String.valueOf(m.role()), "content", Redactor.redact(String.valueOf(m.content()), patient)))
                .toList();

        Map<String, Object> reply = agents.processIntake(patient.getGraphId().toString(), patient.getPreferredLanguage(), history);
        return new IntakeResponse(String.valueOf(reply.get("next_question")), Boolean.TRUE.equals(reply.get("is_complete")));
    }
}
