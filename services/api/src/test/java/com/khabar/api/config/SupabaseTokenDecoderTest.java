package com.khabar.api.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Supabase sign-ins are ES256, checked against the project's published keys; the demo buttons'
 * tokens are HS256 with a shared secret. The deployed API accepts both, each only against its own key.
 */
class SupabaseTokenDecoderTest {

    static final String SECRET = "demo-secret-for-this-test-with-at-least-32-bytes";
    static final SecurityConfig config = new SecurityConfig();

    static ECKey supabaseKey;
    static HttpServer jwks;
    static String jwksUrl;

    @BeforeAll
    static void publishKeys() throws Exception {
        supabaseKey = new ECKeyGenerator(Curve.P_256).keyID("supabase-test").generate();
        byte[] body = new JWKSet(supabaseKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwks.createContext("/auth/v1/.well-known/jwks.json", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        jwks.start();
        jwksUrl = "http://127.0.0.1:" + jwks.getAddress().getPort() + "/auth/v1/.well-known/jwks.json";
    }

    @AfterAll
    static void stop() {
        jwks.stop(0);
    }

    @Test
    void acceptsARealSupabaseSignInAndADemoTokenSideBySide() throws Exception {
        JwtDecoder decoder = config.jwtDecoder(jwksUrl, SECRET);
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();

        assertThat(decoder.decode(supabaseToken(patient, "authenticated", 3_600_000)).getSubject()).isEqualTo(patient.toString());
        assertThat(decoder.decode(demoToken(doctor, SECRET)).getSubject()).isEqualTo(doctor.toString());
    }

    @Test
    void worksWithOnlyTheSupabaseKeysConfigured() throws Exception {
        JwtDecoder decoder = config.jwtDecoder(jwksUrl, "");
        UUID patient = UUID.randomUUID();

        assertThat(decoder.decode(supabaseToken(patient, "authenticated", 3_600_000)).getSubject()).isEqualTo(patient.toString());
        assertThatThrownBy(() -> decoder.decode(demoToken(patient, SECRET)))
                .isInstanceOf(JwtException.class).hasMessageContaining("HS256");
    }

    @Test
    void refusesAnAsymmetricTokenWhenOnlyTheDemoSecretIsConfigured() throws Exception {
        JwtDecoder decoder = config.jwtDecoder("", SECRET);

        assertThatThrownBy(() -> decoder.decode(supabaseToken(UUID.randomUUID(), "authenticated", 3_600_000)))
                .isInstanceOf(JwtException.class).hasMessageContaining("ES256");
    }

    @Test
    void refusesATokenSignedByAnotherSupabaseProject() throws Exception {
        ECKey otherProject = new ECKeyGenerator(Curve.P_256).keyID("supabase-test").generate();

        assertThatThrownBy(() -> config.jwtDecoder(jwksUrl, SECRET).decode(sign(new ECDSASigner(otherProject),
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("supabase-test").build(), claims(UUID.randomUUID(), "authenticated", 3_600_000))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesADemoTokenSignedWithTheWrongSecret() {
        assertThatThrownBy(() -> config.jwtDecoder(jwksUrl, SECRET).decode(demoToken(UUID.randomUUID(), "another-secret-that-is-also-at-least-32-bytes")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesExpiredTokensAndTokensForAnotherAudience() {
        JwtDecoder decoder = config.jwtDecoder(jwksUrl, SECRET);

        assertThatThrownBy(() -> decoder.decode(supabaseToken(UUID.randomUUID(), "authenticated", -3_600_000)))
                .isInstanceOf(JwtException.class).hasMessageContaining("expired");
        assertThatThrownBy(() -> decoder.decode(supabaseToken(UUID.randomUUID(), "anon", 3_600_000)))
                .isInstanceOf(JwtException.class).hasMessageContaining("aud");
    }

    @Test
    void refusesUnsignedAndMalformedTokens() {
        JwtDecoder decoder = config.jwtDecoder(jwksUrl, SECRET);
        String unsigned = new PlainJWT(claims(UUID.randomUUID(), "authenticated", 3_600_000)).serialize();

        assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("not-a-token")).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesToStartWithoutAnyWayToCheckSignIns() {
        assertThatThrownBy(() -> config.jwtDecoder("", "too-short"))
                .isInstanceOf(IllegalStateException.class);
    }

    static String supabaseToken(UUID user, String audience, long expiresInMillis) throws JOSEException {
        return sign(new ECDSASigner(supabaseKey), new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(supabaseKey.getKeyID()).build(),
                claims(user, audience, expiresInMillis));
    }

    static String demoToken(UUID user, String secret) throws JOSEException {
        return sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)), new JWSHeader(JWSAlgorithm.HS256),
                claims(user, "authenticated", 3_600_000));
    }

    /** Supabase access tokens last an hour, so issue time is always an hour before expiry. */
    static JWTClaimsSet claims(UUID user, String audience, long expiresInMillis) {
        long expiresAt = System.currentTimeMillis() + expiresInMillis;
        return new JWTClaimsSet.Builder()
                .subject(user.toString())
                .audience(audience)
                .claim("role", "authenticated")
                .issueTime(new Date(expiresAt - 3_600_000))
                .expirationTime(new Date(expiresAt))
                .build();
    }

    static String sign(JWSSigner signer, JWSHeader header, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
