package com.khabar.api.staffalerts;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * The clinic's staff chat: link it, switch alerts and the morning briefing on or off, send a test, and see every
 * message sent to it. Doctors and clinic administrators manage it; nurses can read it.
 */
@RestController
@RequestMapping("/api/clinic/staff-chat")
public class StaffChannelController {

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final StaffAlerts alerts;

    public StaffChannelController(CurrentUser currentUser, ClinicStaffAccess staffAccess, StaffAlerts alerts) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.alerts = alerts;
    }

    public record ChannelView(boolean linked, Instant linkedAt, boolean alertsOn, Integer briefingHour, Integer handoverHour,
                              boolean telegramConfigured, boolean pendingCode, List<StaffAlerts.LogView> recent) {
    }

    public record ChannelInput(Boolean alertsOn, Integer briefingHour, Integer handoverHour) {
    }

    public record Preview(String text) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ChannelView view(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireStaff(jwt);
        return view(actor);
    }

    @PutMapping
    @Transactional
    public ChannelView update(@RequestBody ChannelInput input, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        if (input == null || input.alertsOn() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say whether staff alerts are on.");
        }
        if (input.briefingHour() != null && (input.briefingHour() < 0 || input.briefingHour() > 23)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The briefing hour must be between 0 and 23.");
        }
        if (input.handoverHour() != null && (input.handoverHour() < 0 || input.handoverHour() > 23)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The handover hour must be between 0 and 23.");
        }
        alerts.configure(actor, input.alertsOn(), input.briefingHour(), input.handoverHour());
        return view(actor);
    }

    @PostMapping("/link-code")
    @Transactional
    public StaffAlerts.LinkCode linkCode(@AuthenticationPrincipal Jwt jwt) {
        return alerts.newLinkCode(requireManager(jwt));
    }

    @DeleteMapping
    @Transactional
    public ChannelView unlink(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        alerts.unlink(actor);
        return view(actor);
    }

    @PostMapping("/test")
    @Transactional
    public StaffAlerts.LogView test(@AuthenticationPrincipal Jwt jwt) {
        return alerts.sendTest(requireManager(jwt));
    }

    /** Sends today's briefing now, whatever the hour. */
    @PostMapping("/briefing")
    @Transactional
    public StaffAlerts.LogView briefingNow(@AuthenticationPrincipal Jwt jwt) {
        return alerts.sendBriefingNow(requireManager(jwt));
    }

    /** Sends the end-of-shift handover now. */
    @PostMapping("/handover")
    @Transactional
    public StaffAlerts.LogView handoverNow(@AuthenticationPrincipal Jwt jwt) {
        return alerts.sendHandoverNow(requireManager(jwt));
    }

    /** What the handover would say right now, without sending it. */
    @GetMapping("/handover")
    @Transactional(readOnly = true)
    public Preview handover(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireStaff(jwt);
        return new Preview(alerts.handoverText(actor.getClinic().getId()));
    }

    /** What the morning briefing would say right now, without sending it. */
    @GetMapping("/briefing")
    @Transactional(readOnly = true)
    public Preview briefing(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireStaff(jwt);
        return new Preview(alerts.briefingText(actor.getClinic().getId()));
    }

    private ChannelView view(AppUser actor) {
        StaffChannel c = alerts.channelFor(actor.getClinic().getId());
        boolean pending = alerts.pendingCode(c);
        return new ChannelView(c.getChatId() != null, c.getLinkedAt(), c.isAlertsOn(), c.getBriefingHour(), c.getHandoverHour(),
                alerts.telegramConfigured(), pending, alerts.recent(actor.getClinic().getId()));
    }

    private AppUser requireStaff(Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !(staffAccess.canManageStaff(actor) || staffAccess.canManageFollowUp(actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The staff chat is for clinic staff.");
        }
        return actor;
    }

    private AppUser requireManager(Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !staffAccess.canManageStaff(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clinic doctors or administrators can manage the staff chat.");
        }
        return actor;
    }
}
