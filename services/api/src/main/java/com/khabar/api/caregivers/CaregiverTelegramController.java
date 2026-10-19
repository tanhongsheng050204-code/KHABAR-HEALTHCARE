package com.khabar.api.caregivers;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** A caregiver links, checks or unlinks the Telegram chat that gets their alerts. */
@RestController
@RequestMapping("/api/me/caregiver-telegram")
public class CaregiverTelegramController {

    private final CurrentUser currentUser;
    private final CaregiverAlerts alerts;

    public CaregiverTelegramController(CurrentUser currentUser, CaregiverAlerts alerts) {
        this.currentUser = currentUser;
        this.alerts = alerts;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public CaregiverAlerts.Status status(@AuthenticationPrincipal Jwt jwt) {
        return alerts.status(caregiver(jwt));
    }

    @PostMapping("/link-code")
    @Transactional
    public CaregiverAlerts.LinkCode linkCode(@AuthenticationPrincipal Jwt jwt) {
        return alerts.newLinkCode(caregiver(jwt));
    }

    @DeleteMapping
    @Transactional
    public CaregiverAlerts.Status unlink(@AuthenticationPrincipal Jwt jwt) {
        AppUser caregiver = caregiver(jwt);
        alerts.unlink(caregiver);
        return alerts.status(caregiver);
    }

    private AppUser caregiver(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.CAREGIVER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Telegram alerts are for family caregivers.");
        }
        return user;
    }
}
