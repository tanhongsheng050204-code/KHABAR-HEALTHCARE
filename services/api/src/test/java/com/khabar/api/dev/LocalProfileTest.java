package com.khabar.api.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @Autowired com.khabar.api.patients.PatientRepository patients;
    @Autowired com.khabar.api.intake.IntakeSessionRepository intakes;

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
    void aminahArrivesWithHerConditionsAndABookedAppointment() throws Exception {
        String patientId = aminahsId();

        mvc.perform(get("/api/patients/{id}/intake", patientId).header("Authorization", tokenFor("doctor")))
                .andExpect(jsonPath("$.report.conditions[0]").value("diabetes"))
                .andExpect(jsonPath("$.report.conditions[1]").value("hypertension"));
        mvc.perform(get("/api/appointments/mine").header("Authorization", tokenFor("patient")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void resettingTheDemoPutsAminahsWholeStoryBack() throws Exception {
        String patient = tokenFor("patient");
        String patientId = aminahsId();
        String path = "/api/patients/" + patientId + "/medications";

        // A demo run changes her side of the story: an item stopped and one added, her daughter's consent
        // withdrawn, her appointment cancelled.
        String list = mvc.perform(get(path).header("Authorization", patient)).andReturn().getResponse().getContentAsString();
        mvc.perform(delete(path + "/{item}", json.readTree(list).get(0).get("id").asText()).header("Authorization", patient))
                .andExpect(status().isOk());
        mvc.perform(post(path).header("Authorization", patient).contentType("application/json")
                        .content("{\"name\":\"Panadol\",\"kind\":\"MEDICINE\",\"source\":\"Pharmacy\"}"))
                .andExpect(status().isCreated());
        String caregivers = mvc.perform(get("/api/patients/me/caregivers").header("Authorization", patient))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/patients/me/caregivers/{id}", json.readTree(caregivers).get(0).get("linkId").asText()).header("Authorization", patient))
                .andExpect(status().isOk());
        String booking = mvc.perform(get("/api/appointments/mine").header("Authorization", patient)).andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/appointments/{id}", json.readTree(booking).get("id").asText()).header("Authorization", patient))
                .andExpect(status().isOk());

        mvc.perform(post("/dev/demo/reset")).andExpect(status().isOk());

        mvc.perform(get(path).header("Authorization", tokenFor("doctor")))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.name == 'Panadol')]").isEmpty());
        mvc.perform(get("/api/patients/{id}", patientId).header("Authorization", tokenFor("caregiver")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/appointments/mine").header("Authorization", patient))
                .andExpect(status().isOk());
        mvc.perform(get("/api/patients/{id}/intake", patientId).header("Authorization", tokenFor("doctor")))
                .andExpect(jsonPath("$.report.conditions.length()").value(2));
    }

    @Test
    void resettingRepairsAnIntakeSavedBeforeConditionsWereRecorded() throws Exception {
        String patientId = aminahsId();
        // What the live database held: an intake report written by an older version, with no conditions.
        var aminah = patients.findById(java.util.UUID.fromString(patientId)).orElseThrow();
        var old = new com.khabar.api.intake.IntakeSession(aminah, java.time.Instant.now().minusSeconds(60));
        old.complete("{\"reason\":\"Pening sejak 3 hari, kadang-kadang berpeluh.\",\"answers\":[],\"medicines\":[],\"herbs\":[]}",
                java.time.Instant.now());
        intakes.save(old);
        mvc.perform(get("/api/patients/{id}/intake", patientId).header("Authorization", tokenFor("doctor")))
                .andExpect(jsonPath("$.report.conditions").doesNotExist());

        mvc.perform(post("/dev/demo/reset")).andExpect(status().isOk());

        mvc.perform(get("/api/patients/{id}/intake", patientId).header("Authorization", tokenFor("doctor")))
                .andExpect(jsonPath("$.report.conditions[0]").value("diabetes"));
    }

    private String aminahsId() throws Exception {
        String me = mvc.perform(get("/api/me").header("Authorization", tokenFor("patient")))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(me).get("patientId").asText();
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
