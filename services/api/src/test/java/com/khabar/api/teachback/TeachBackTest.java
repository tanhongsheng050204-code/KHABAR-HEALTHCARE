package com.khabar.api.teachback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.encounters.EncounterRepository;
import com.khabar.api.encounters.TestVisits;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.encounters.TestVisits.rx;
import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Check you understood your plan": questions from the doctor's own plan, answers shown straight after. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeachBackTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ClinicStaffAccess staffAccess;
    @Autowired EncounterRepository encounters;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @Autowired AuditLog auditLog;
    @MockBean AgentClientService agents;

    AppUser doctor, account;
    Patient aminah;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Faham"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        account = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        aminah = patients.save(new Patient(clinic, account, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
    }

    @Test
    void withoutASignedPlanThereIsNothingToCheck() throws Exception {
        mvc.perform(get("/api/me/teach-back").header("Authorization", bearer(account.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions").isEmpty());
    }

    @Test
    void questionsComeFromThePlanAndTheClinicSeesWhatWasMissed() throws Exception {
        encounters.save(TestVisits.signed(aminah, doctor, clock.instant(), false, 4.0, List.of(
                rx("Paracetamol", 500, List.of("morning"), null, true),
                rx("Metformin", 500, List.of("morning", "night"), "after_food", false))));

        JsonNode quiz = json.readTree(mvc.perform(get("/api/me/teach-back").header("Authorization", bearer(account.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode questions = quiz.get("questions");
        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).get("kind").asText()).isEqualTo("TIMES_PER_DAY");
        assertThat(questions.get(0).get("medicine").asText()).as("as-needed medicines are not asked about").isEqualTo("Metformin");
        assertThat(questions.get(1).get("kind").asText()).isEqualTo("FOOD");
        assertThat(questions.get(2).get("options")).extracting(JsonNode::asText).containsExactly("2", "4", "8");
        assertThat(quiz.toString()).as("the right answers are not sent with the questions").doesNotContain("answer");

        String body = json.writeValueAsString(Map.of("encounterId", quiz.get("encounterId").asText(),
                "answers", Map.of("times:0", "2", "food:0", "before_food", "next_visit", "4")));
        mvc.perform(post("/api/me/teach-back").header("Authorization", bearer(account.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.results[1].answer").value("after_food"));

        mvc.perform(get("/api/patients/{id}/teach-back", aminah.getId()).header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correct").value(2))
                .andExpect(jsonPath("$[0].missed[0]").value("food:0"));
        assertThat(auditLog.forPatient(aminah.getId())).anyMatch(e -> e.getAction() == AuditAction.VIEWED_RECORD);

        AppUser caregiver = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Siti", null));
        caregiverLinks.save(new CaregiverLink(aminah, caregiver, CaregiverScope.SUMMARY_AND_ALERTS));
        mvc.perform(get("/api/patients/{id}/teach-back", aminah.getId()).header("Authorization", bearer(caregiver.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clinic/impact").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics[?(@.key=='teachBackAllRight')].of").value(1));
    }

    @Test
    void answersForAnOlderPlanAreRefused() throws Exception {
        encounters.save(TestVisits.signed(aminah, doctor, clock.instant(), false, 2.0, List.of(
                rx("Metformin", 500, List.of("morning"), "after_food", false))));
        mvc.perform(post("/api/me/teach-back").header("Authorization", bearer(account.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("encounterId", UUID.randomUUID().toString(), "answers", Map.of()))))
                .andExpect(status().isConflict());
    }
}
