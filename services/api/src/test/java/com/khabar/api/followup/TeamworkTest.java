package com.khabar.api.followup;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Case notes with @mentions, and the clinic's impact summary. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamworkTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ClinicStaffAccess staffAccess;
    @MockBean AgentClientService agents;

    AppUser doctor, nurse, admin, otherDoctor, aminahAccount;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Pasukan"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        nurse = users.save(new AppUser(UUID.randomUUID(), Role.NURSE, "Nurse Mei", clinic));
        staffAccess.grant(nurse, clinic, ClinicStaffRole.NURSE, doctor.getId(), clock.instant());
        admin = users.save(new AppUser(UUID.randomUUID(), Role.CLINIC_ADMIN, "Admin Farah", clinic));
        staffAccess.grant(admin, clinic, ClinicStaffRole.CLINIC_ADMIN, doctor.getId(), clock.instant());
        otherDoctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        staffAccess.grant(otherDoctor, other, ClinicStaffRole.DOCTOR, otherDoctor.getId(), clock.instant());
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        Patient p = new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms");
        p.startFollowUp(LocalDate.now(clock).minusDays(3));
        patients.save(p);
    }

    @Test
    void aNoteMentioningAColleagueShowsInTheirMentionsUntilRead() throws Exception {
        String caseId = openUrgentCase();
        mvc.perform(post("/api/clinic/cases/{id}/notes", caseId).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("text", "@Nurse Mei please call her after 3pm, she works mornings."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.by").value("Dr Priya"))
                .andExpect(jsonPath("$.mentions[0].displayName").value("Nurse Mei"));

        mvc.perform(get("/api/clinic/mentions").header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.items[0].patientName").value("Aminah binti Yusof"));
        mvc.perform(post("/api/clinic/mentions/seen").header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/clinic/mentions").header("Authorization", bearer(doctor.getId())))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/clinic/cases/{id}/notes", caseId).header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("@Nurse Mei please call her after 3pm, she works mornings."));
    }

    @Test
    void notesStayInTheClinicAndOutOfAdministratorsHands() throws Exception {
        String caseId = openUrgentCase();
        mvc.perform(get("/api/clinic/cases/{id}/notes", caseId).header("Authorization", bearer(otherDoctor.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/clinic/cases/{id}/notes", caseId).header("Authorization", bearer(admin.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/clinic/cases/{id}/notes", caseId).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mentionsMatchTheLongestNameFirst() {
        AppUser priya = new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", null);
        AppUser priyaNair = new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya Nair", null);
        assertThat(CaseNotesController.mentionsIn("Ask @dr priya nair today", List.of(priya, priyaNair))).containsExactly(priyaNair.getId());
        assertThat(CaseNotesController.mentionsIn("no mention here", List.of(priya))).isEmpty();
    }

    @Test
    void theImpactSummaryCountsFromTheClinicsOwnRecords() throws Exception {
        String caseId = openUrgentCase();
        mvc.perform(post("/api/clinic/cases/{id}/acknowledge", caseId).header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isOk());

        String body = mvc.perform(get("/api/clinic/impact?days=30").header("Authorization", bearer(admin.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode metrics = json.readTree(body).get("metrics");
        JsonNode ack = find(metrics, "ackOnTime");
        assertThat(ack.get("value").asLong()).isEqualTo(1);
        assertThat(ack.get("of").asLong()).isEqualTo(1);
        assertThat(find(metrics, "urgentReplies").get("value").asLong()).isEqualTo(1);
        assertThat(json.readTree(body).get("medianAckMinutes").asLong()).isZero();

        mvc.perform(get("/api/clinic/impact?days=0").header("Authorization", bearer(admin.getId())))
                .andExpect(status().isBadRequest());
    }

    private static JsonNode find(JsonNode metrics, String key) {
        for (JsonNode m : metrics) {
            if (m.get("key").asText().equals(key)) {
                return m;
            }
        }
        throw new AssertionError("No metric " + key);
    }

    private String openUrgentCase() throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "red", "matched", "sakit dada"));
        mvc.perform(post("/api/followup/replies").header("Authorization", bearer(aminahAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", "sakit dada sejak pagi"))))
                .andExpect(status().isOk());
        String list = mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(list).get("items").get(0).get("followUpCase").get("id").asText();
    }
}
