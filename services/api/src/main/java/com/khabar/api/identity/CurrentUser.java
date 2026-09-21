package com.khabar.api.identity;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Turns a verified Supabase token into the Khabar user it belongs to. */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppUser from(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        return users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is not registered with a Khabar clinic yet."));
    }
}
