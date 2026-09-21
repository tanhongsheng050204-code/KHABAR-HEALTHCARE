package com.khabar.api.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/** Mints Supabase-style access tokens (HS256) for tests, using the secret in application-test.yml. */
public final class TestTokens {

    public static final String SECRET = "test-supabase-jwt-secret-with-at-least-32-bytes!!";

    private TestTokens() {
    }

    public static String bearer(UUID userId) {
        return "Bearer " + sign(userId, SECRET);
    }

    public static String bearerSignedWith(UUID userId, String otherSecret) {
        return "Bearer " + sign(userId, otherSecret);
    }

    private static String sign(UUID userId, String secret) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .audience("authenticated")
                    .claim("role", "authenticated")
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
