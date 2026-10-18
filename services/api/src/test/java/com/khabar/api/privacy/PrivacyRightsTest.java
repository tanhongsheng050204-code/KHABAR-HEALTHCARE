package com.khabar.api.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDPA rights: export, erasure and correction requests, and the breach register. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivacyRightsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @Autowired ClinicStaffAccess staffAccess;
    @MockBean AgentClientService agents;

    AppUser doctor, nurse, aminahAccount, nurul;
    Patient aminah;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Privasi"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        nurse = users.save(new AppUser(UUID.randomUUID(), Role.NURSE, "Nurse Mei", clinic));
        staffAccess.grant(nurse, clinic, ClinicStaffRole.NURSE, doctor.getId(), clock.instant());
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        nurul = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Nurul", null));
        aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));
    }

    @Test
    void aPatientDownloadsEverythingHeldAboutThem() throws Exception {
        mvc.perform(get("/api/me/export").header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("khabar-my-data.json")))
                .andExpect(jsonPath("$.profile.name").value("Aminah binti Yusof"))
                .andExpect(jsonPath("$.profile.icNumber").value("590312-10-5566"))
                .andExpect(jsonPath("$.caregivers[0].name").value("Nurul"));
        mvc.perform(get("/api/me/export").header("Authorization", bearer(nurul.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anErasureRequestDecidedByTheClinicRemovesIdentityAndKeepsTheRecord() throws Exception {
        String body = mvc.perform(post("/api/me/privacy-requests").header("Authorization", bearer(aminahAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("kind", "ERASURE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        mvc.perform(post("/api/me/privacy-requests").header("Authorization", bearer(aminahAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("kind", "ERASURE"))))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/clinic/privacy-requests").header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/clinic/privacy-requests/{id}/resolve", id).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "DONE", "resolution", "Identity erased; clinical notes kept for 7 years.", "eraseIdentity", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        Patient erased = patients.findById(aminah.getId()).orElseThrow();
        assertThat(erased.getFullName()).startsWith("Erased patient ");
        assertThat(erased.getIcNumber()).isNull();
        assertThat(erased.getPhone()).isNull();
        assertThat(erased.getAccount()).isNull();
        assertThat(caregiverLinks.findByPatientIdAndRevokedAtIsNull(aminah.getId())).isEmpty();
    }

    @Test
    void aBreachIsWorkedThroughTheChecklistBeforeItCanClose() throws Exception {
        String body = mvc.perform(post("/api/clinic/incidents").header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Laptop left in a taxi", "whatHappened", "Locked laptop, encrypted disk.", "peopleAffected", 0))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();

        step(id, "CLOSED", null).andExpect(status().isConflict());
        step(id, "CONTAINED", null).andExpect(status().isOk());
        step(id, "ASSESSED", null).andExpect(status().isBadRequest());
        step(id, "ASSESSED", true).andExpect(status().isOk()).andExpect(jsonPath("$.significantHarm").value(true));
        step(id, "COMMISSIONER_NOTIFIED", null).andExpect(status().isOk());
        step(id, "CLOSED", null).andExpect(status().isConflict());
        step(id, "PEOPLE_NOTIFIED", null).andExpect(status().isOk());
        JsonNode closed = json.readTree(step(id, "CLOSED", null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(closed.get("log").asText()).contains("CONTAINED by Dr Priya").contains("CLOSED by Dr Priya");
    }

    private org.springframework.test.web.servlet.ResultActions step(String id, String step, Boolean harm) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("step", step);
        body.put("significantHarm", harm);
        return mvc.perform(post("/api/clinic/incidents/{id}/steps", id).header("Authorization", bearer(doctor.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
