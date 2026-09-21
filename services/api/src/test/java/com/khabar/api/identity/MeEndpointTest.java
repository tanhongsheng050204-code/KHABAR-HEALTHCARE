package com.khabar.api.identity;

import com.khabar.api.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeEndpointTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ClinicRepository clinics;
    @Autowired
    AppUserRepository users;

    UUID doctorId;

    @BeforeEach
    void setUp() {
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        doctorId = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic)).getId();
    }

    @Test
    void rejectsRequestsWithoutAToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATokenSignedWithTheWrongSecret() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", TestTokens.bearerSignedWith(doctorId, "some-other-secret-that-is-also-32-bytes-long")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheSignedInDoctor() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", TestTokens.bearer(doctorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.displayName").value("Dr Priya"))
                .andExpect(jsonPath("$.clinicName").value("Klinik Dr Priya"));
    }

    @Test
    void refusesAValidTokenForSomeoneNotRegisteredWithAClinic() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", TestTokens.bearer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void devTokensDoNotExistOutsideTheLocalProfile() throws Exception {
        // No such endpoint outside `local`: Spring answers 404 (or 405 from the static-resource fallback), never a token.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/dev/token").param("as", "doctor"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    @Test
    void healthStaysPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
