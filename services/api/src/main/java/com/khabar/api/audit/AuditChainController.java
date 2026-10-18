package com.khabar.api.audit;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The anchor endpoints are public on purpose: they show only a position and a hash, never who or what, so a
 * daily job (.github/workflows/audit-anchor.yml) can copy them into the repository without credentials, and
 * anyone can later compare a copied anchor with the live chain. Verifying the whole chain is for clinic
 * doctors and administrators.
 */
@RestController
public class AuditChainController {

    private final AuditChain chain;
    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;

    public AuditChainController(AuditChain chain, CurrentUser currentUser, ClinicStaffAccess staffAccess) {
        this.chain = chain;
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
    }

    public record AnchorAt(long seq, String hash) {
    }

    @GetMapping("/api/audit/anchor")
    public AuditChain.Anchor anchor() {
        return chain.anchor();
    }

    @GetMapping("/api/audit/anchor/{seq}")
    public AnchorAt hashAt(@PathVariable long seq) {
        String hash = chain.hashAt(seq);
        if (hash == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No sealed entry at that position.");
        }
        return new AnchorAt(seq, hash);
    }

    @GetMapping("/api/clinic/audit/verify")
    public AuditChain.Verification verify(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !staffAccess.canManageStaff(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clinic doctors or administrators can verify the audit chain.");
        }
        return chain.verify();
    }
}
