package com.khabar.api.followup;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffGrantRepository;
import com.khabar.api.identity.CurrentUser;
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
import java.util.List;
import java.util.UUID;

/** Assign, acknowledge, record a call, escalate and close follow-up cases. Clinic doctors and nurses only. */
@RestController
@RequestMapping("/api/clinic/cases")
public class FollowUpCaseController {

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final ClinicStaffGrantRepository grants;
    private final FollowUpCases cases;

    public FollowUpCaseController(CurrentUser currentUser, ClinicStaffAccess staffAccess, ClinicStaffGrantRepository grants,
                                  FollowUpCases cases) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.grants = grants;
        this.cases = cases;
    }

    public record AssignRequest(UUID ownerId) {
    }

    public record ContactRequest(String outcome, String note) {
    }

    public record EscalateRequest(UUID toUserId, String note) {
    }

    public record CloseRequest(ClosureReason reason, String note, Instant observedThrough) {
    }

    public record History(FollowUpCases.CaseView followUpCase, List<FollowUpCases.EventView> events) {
    }

    public record Assignee(UUID userId, String displayName, List<String> roles) {
    }

    /** Doctors and nurses a case can be assigned to. */
    @GetMapping("/assignees")
    @Transactional(readOnly = true)
    public List<Assignee> assignees(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        return grants.findByClinicIdAndRevokedAtIsNullOrderByGrantedAt(actor.getClinic().getId()).stream()
                .map(g -> g.getAppUser())
                .distinct()
                .filter(staffAccess::canManageFollowUp)
                .map(u -> new Assignee(u.getId(), u.getDisplayName(), staffAccess.rolesFor(u).stream().map(Enum::name).toList()))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public History history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        FollowUpCase c = cases.caseFor(actor, id);
        return new History(cases.view(c), cases.history(c));
    }

    @PostMapping("/{id}/assign")
    @Transactional
    public FollowUpCases.CaseView assign(@PathVariable UUID id, @RequestBody(required = false) AssignRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        return cases.assign(actor, cases.caseFor(actor, id), request == null ? null : request.ownerId());
    }

    @PostMapping("/{id}/acknowledge")
    @Transactional
    public FollowUpCases.CaseView acknowledge(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        return cases.acknowledge(actor, cases.caseFor(actor, id));
    }

    @PostMapping("/{id}/contact")
    @Transactional
    public FollowUpCases.CaseView contact(@PathVariable UUID id, @RequestBody ContactRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        if (request == null || !("REACHED".equals(request.outcome()) || "NO_ANSWER".equals(request.outcome()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say whether the patient was reached: REACHED or NO_ANSWER.");
        }
        return cases.contact(actor, cases.caseFor(actor, id), "REACHED".equals(request.outcome()), request.note());
    }

    @PostMapping("/{id}/escalate")
    @Transactional
    public FollowUpCases.CaseView escalate(@PathVariable UUID id, @RequestBody EscalateRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        return cases.escalate(actor, cases.caseFor(actor, id), request == null ? null : request.toUserId(), request == null ? null : request.note());
    }

    @PostMapping("/{id}/close")
    @Transactional
    public FollowUpCases.CaseView close(@PathVariable UUID id, @RequestBody CloseRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireFollowUpStaff(jwt);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose why the case is being closed.");
        }
        return cases.close(actor, cases.caseFor(actor, id), request.reason(), request.note(), request.observedThrough());
    }

    private AppUser requireFollowUpStaff(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (!staffAccess.canManageFollowUp(user) || user.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Follow-up cases are for clinic doctors and nurses.");
        }
        return user;
    }
}
