package com.khabar.api.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The `local` profile must run with zero setup: seeded demo data and a way to sign in. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalProfileTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenFor(String who) throws Exception {
        String body = mvc.perform(post("/dev/token").param("as", who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return "Bearer " + node.get("token").asText();
    }

    @Test
    void demoDoctorCanSignIn() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", tokenFor("doctor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.displayName").value("Dr Priya"));
    }

    @Test
    void demoPatientCanSignIn() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", tokenFor("patient")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void demoCaregiverCanSeeTheDemoPatient() throws Exception {
        String me = mvc.perform(get("/api/me").header("Authorization", tokenFor("patient")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String patientId = json.readTree(me).get("patientId").asText();

        mvc.perform(get("/api/patients/{id}", patientId).header("Authorization", tokenFor("caregiver")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Aminah binti Yusof"));
    }

    @Test
    void demoDoctorHasACallListWithAnUrgentPatientFirst() throws Exception {
        mvc.perform(get("/api/clinic/call-list").header("Authorization", tokenFor("doctor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].level").value("RED"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.patientsInFollowUp").value(4));
    }

    @Test
    void theScreensServedLocallyMayCallTheApi() throws Exception {
        for (String path : new String[]{"/dev/token", "/api/clinic/call-list"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(path)
                            .header("Origin", "http://localhost:5500")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Access-Control-Allow-Origin", "http://localhost:5500"));
        }
    }

    @Test
    void unknownDemoUserIsABadRequest() throws Exception {
        mvc.perform(post("/dev/token").param("as", "admin")).andExpect(status().isBadRequest());
    }
}
