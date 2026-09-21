package com.khabar.api.dev;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * `local` profile only: hands out a signed token for a demo user, so the API can be
 * tried with curl before Supabase is connected. Does not exist in any other profile.
 */
@RestController
@RequestMapping("/dev")
@Profile("local")
public class DevTokenController {

    private static final Map<String, UUID> DEMO_USERS = Map.of(
            "doctor", DemoData.DOCTOR_ID,
            "patient", DemoData.AMINAH_ACCOUNT_ID,
            "caregiver", DemoData.NURUL_ID);

    private final String secret;

    public DevTokenController(@Value("${khabar.security.supabase-jwt-secret}") String secret) {
        this.secret = secret;
    }

    public record DevToken(String token, UUID userId) {
    }

    @PostMapping("/token")
    public DevToken token(@RequestParam("as") String who) throws JOSEException {
        UUID userId = DEMO_USERS.get(who);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use as=doctor, as=patient or as=caregiver");
        }
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .audience("authenticated")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 12 * 3_600_000L))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return new DevToken(jwt.serialize(), userId);
    }
}
