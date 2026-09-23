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
    void theDemoDoctorSeesAminahsPreVisitReportAndWhatSheTakesElsewhere() throws Exception {
        String me = mvc.perform(get("/api/me").header("Authorization", tokenFor("patient")))
                .andReturn().getResponse().getContentAsString();
        String patientId = json.readTree(me).get("patientId").asText();

        mvc.perform(get("/api/patients/{id}/intake", patientId).header("Authorization", tokenFor("doctor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.medicines.length()").value(2))
                .andExpect(jsonPath("$.report.herbs[0]").value("peria"))
                .andExpect(jsonPath("$.report.redFlags[0].matched").value("pening"));
        mvc.perform(get("/api/patients/{id}/medications", patientId).header("Authorization", tokenFor("doctor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void theDemoClinicHasApprovedAnswersInEveryLanguage() throws Exception {
        mvc.perform(get("/api/clinic/answers").header("Authorization", tokenFor("doctor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].texts.ta").exists())
                .andExpect(jsonPath("$[1].texts.zh").exists());
    }

    @Test
    void theDemoClinicHasThirtyFakePatients() throws Exception {
        mvc.perform(get("/api/clinic/patients").header("Authorization", tokenFor("doctor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30));
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
    void theDemoCanBePutBackForTheNextRun() throws Exception {
        String doctor = tokenFor("doctor");
        String body = mvc.perform(get("/api/clinic/call-list").header("Authorization", doctor))
                .andReturn().getResponse().getContentAsString();
        String urgentPatient = json.readTree(body).get("items").get(0).get("patientId").asText();
        mvc.perform(post("/api/clinic/call-list/{id}/called", urgentPatient).header("Authorization", doctor))
                .andExpect(status().isOk());
        mvc.perform(get("/api/clinic/call-list").header("Authorization", doctor))
                .andExpect(jsonPath("$.items.length()").value(2));

        mvc.perform(post("/dev/demo/reset")).andExpect(status().isOk())
                .andExpect(jsonPath("$.replies").value(4));

        mvc.perform(get("/api/clinic/call-list").header("Authorization", doctor))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].level").value("RED"));
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
