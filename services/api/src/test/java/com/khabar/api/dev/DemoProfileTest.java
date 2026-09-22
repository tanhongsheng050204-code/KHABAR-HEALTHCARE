package com.khabar.api.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public demo runs `local,demo`: the demo people and demo sign-in of `local`, but with its own
 * secrets and database from the environment, and only the deployed site allowed to call it.
 * (H2 stands in for the real Postgres here.)
 */
@SpringBootTest(properties = {
        "SPRING_DATASOURCE_URL=jdbc:h2:mem:khabar-demo-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "SPRING_DATASOURCE_USERNAME=sa",
        "SPRING_DATASOURCE_PASSWORD=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "INTERNAL_SERVICE_KEY=demo-internal-key",
        "SUPABASE_JWT_SECRET=a-demo-secret-that-is-not-in-the-repository-00",
        "FIELD_ENCRYPTION_KEY=0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0",
        "AGENTS_SERVICE_URL=http://agents.demo.test",
        "WEB_ALLOWED_ORIGINS=https://khabar.example"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "demo"})
class DemoProfileTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void theDemoButtonsSignInWithTheDeploymentsOwnSecret() throws Exception {
        String body = mvc.perform(post("/dev/token").param("as", "doctor")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("token").asText();

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCTOR"));
    }

    @Test
    void aTokenSignedWithTheSecretInTheRepositoryIsRefused() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(DemoData.DOCTOR_ID.toString()).audience("authenticated")
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 3_600_000L)).build();
        SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        forged.sign(new MACSigner("local-dev-only-jwt-secret-do-not-use-in-production".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + forged.serialize()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheDeployedSiteMayCallTheApi() throws Exception {
        mvc.perform(options("/api/me").header("Origin", "https://khabar.example").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://khabar.example"));
        mvc.perform(options("/api/me").header("Origin", "http://localhost:3000").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theDemoClinicIsSeeded() throws Exception {
        String body = mvc.perform(post("/dev/token").param("as", "doctor")).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/clinic/patients").header("Authorization", "Bearer " + json.readTree(body).get("token").asText()))
                .andExpect(jsonPath("$.length()").value(30));
    }
}
