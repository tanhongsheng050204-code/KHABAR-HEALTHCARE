package com.khabar.api.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.PatientReply;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.Role;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.readings.Reading;
import com.khabar.api.readings.ReadingRepository;
import com.khabar.api.reminders.DoseLog;
import com.khabar.api.reminders.DoseLogRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Condition pathways, lab results, trends and the follow-up risk score. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InsightsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ClinicStaffAccess staffAccess;
    @Autowired ReadingRepository readings;
    @Autowired DoseLogRepository doses;
    @Autowired PatientReplyRepository replies;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @Autowired Pathways pathways;
    @MockBean AgentClientService agents;

    Clinic clinic;
    AppUser doctor, nurse, account, caregiver;
    Patient ahmad;

    @BeforeEach
    void setUp() {
        clock.reset();
        clinic = clinics.save(new Clinic("Klinik Tren"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        nurse = users.save(new AppUser(UUID.randomUUID(), Role.NURSE, "Nurse Mei", clinic));
        staffAccess.grant(nurse, clinic, ClinicStaffRole.NURSE, doctor.getId(), clock.instant());
        account = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Ahmad", null));
        Patient p = new Patient(clinic, account, "Ahmad bin Ismail", "600921-08-5513", "012-555 0102", "ms");
        p.startFollowUp(LocalDate.now(clock).minusDays(20));
        ahmad = patients.save(p);
        caregiver = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Aisyah", null));
        caregiverLinks.save(new CaregiverLink(ahmad, caregiver, CaregiverScope.SUMMARY_AND_ALERTS));
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void startPathway(String key, Map<String, Object> extra) throws Exception {
        Map<String, Object> input = new java.util.HashMap<>(extra);
        input.put("pathway", key);
        mvc.perform(post("/api/patients/{id}/pathways", ahmad.getId()).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                .andExpect(status().isOk());
    }

    private void glucose(int daysAgo, double mmol) {
        Instant at = clock.instant().minus(Duration.ofDays(daysAgo)).minus(Duration.ofHours(1));
        Reading r = Reading.glucose(ahmad, mmol, at, at, "patient");
        r.markHandled(doctor.getId(), at);
        readings.save(r);
    }

    @Test
    void aDoctorStartsAPathwayWithDefaultTargetsAndTheCheckInAsksForReadings() throws Exception {
        startPathway(Pathways.DIABETES, Map.of());
        startPathway(Pathways.HYPERTENSION, Map.of("systolicMax", 130));
        mvc.perform(post("/api/patients/{id}/pathways", ahmad.getId()).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pathway\":\"DIABETES\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/patients/{id}/pathways", ahmad.getId()).header("Authorization", bearer(nurse.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pathway\":\"POST_OP\"}"))
                .andExpect(status().isForbidden());

        JsonNode list = body(mvc.perform(get("/api/patients/{id}/pathways", ahmad.getId()).header("Authorization", bearer(account.getId())))
                .andExpect(status().isOk()));
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("targets").get("glucoseMax").asDouble()).isEqualTo(7.0);
        assertThat(list.get(1).get("targets").get("systolicMax").asInt()).isEqualTo(130);
        assertThat(pathways.targets(ahmad.getId()).systolicMax()).isEqualTo(130);
        assertThat(pathways.checkInExtra(ahmad.getId(), "ms")).contains("gula darah").contains("tekanan darah");
    }

    @Test
    void labResultsAreJudgedAgainstThePathwayTarget() throws Exception {
        startPathway(Pathways.DIABETES, Map.of("hba1cMax", 7.5));
        mvc.perform(post("/api/patients/{id}/labs", ahmad.getId()).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"test\":\"HBA1C\",\"value\":8.1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value(7.5))
                .andExpect(jsonPath("$.status").value("OUT_OF_TARGET"));
        mvc.perform(post("/api/patients/{id}/labs", ahmad.getId()).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"test\":\"EGFR\",\"value\":75}"))
                .andExpect(jsonPath("$.status").value("IN_TARGET"));
        mvc.perform(post("/api/patients/{id}/labs", ahmad.getId()).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"test\":\"HBA1C\",\"value\":45}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/patients/{id}/labs", ahmad.getId()).header("Authorization", bearer(account.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"test\":\"LDL\",\"value\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rising_sugar_and_missed_doses_show_as_worsening_and_raise_the_score() throws Exception {
        startPathway(Pathways.DIABETES, Map.of());
        for (int d = 13; d >= 8; d--) glucose(d, 7.0);
        for (int d = 6; d >= 1; d--) glucose(d, 9.5);
        LocalDate today = LocalDate.now(clock);
        for (int d = 1; d <= 3; d++) {
            DoseLog dose = new DoseLog(ahmad.getId(), UUID.randomUUID(), today.minusDays(d), "morning", "Metformin", clock.instant());
            dose.answer("MISSED", clock.instant());
            doses.save(dose);
        }

        JsonNode trends = body(mvc.perform(get("/api/patients/{id}/trends", ahmad.getId()).header("Authorization", bearer(caregiver.getId())))
                .andExpect(status().isOk()));
        JsonNode sugar = trends.get("series").get(0);
        assertThat(sugar.get("key").asText()).isEqualTo("GLUCOSE");
        assertThat(sugar.get("direction").asText()).isEqualTo("WORSENING");
        assertThat(sugar.get("outOfTarget").asInt()).isEqualTo(6);
        assertThat(trends.get("overall").asText()).isEqualTo("WORSENING");

        JsonNode risk = body(mvc.perform(get("/api/patients/{id}/risk", ahmad.getId()).header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isOk()));
        // 3 missed doses (3) + sugar rising (2) + latest sugar above target (1)
        assertThat(risk.get("score").asInt()).isEqualTo(6);
        assertThat(risk.get("reasons").toString()).contains("Blood sugar rising").contains("3 missed doses");

        mvc.perform(get("/api/patients/{id}/risk", ahmad.getId()).header("Authorization", bearer(caregiver.getId())))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/clinic/risk-weights").header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"MISSED_DOSES\":0}"))
                .andExpect(status().isOk());
        assertThat(body(mvc.perform(get("/api/patients/{id}/risk", ahmad.getId()).header("Authorization", bearer(nurse.getId()))))
                .get("score").asInt()).isEqualTo(3);
        mvc.perform(put("/api/clinic/risk-weights").header("Authorization", bearer(nurse.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"MISSED_DOSES\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theScoreOrdersPatientsWithinALevelButNeverAboveAnUrgentReply() throws Exception {
        startPathway(Pathways.DIABETES, Map.of());
        for (int d = 13; d >= 8; d--) glucose(d, 7.0);
        for (int d = 6; d >= 1; d--) glucose(d, 9.5);
        Patient calm = patients.save(new Patient(clinic, null, "Siti Hajar", "700112-01-6046", "012-555 0105", "ms"));
        Patient urgent = patients.save(new Patient(clinic, null, "Rosnah", "620505-14-2222", "012-555 0106", "ms"));
        Instant now = clock.instant();
        replies.save(new PatientReply(calm, "Pening sikit", now.minus(Duration.ofMinutes(5)), TriageLevel.REVIEW, null));
        replies.save(new PatientReply(ahmad, "Penat", now.minus(Duration.ofMinutes(50)), TriageLevel.REVIEW, null));
        replies.save(new PatientReply(urgent, "Sakit dada", now.minus(Duration.ofMinutes(90)), TriageLevel.RED, "sakit dada"));

        JsonNode items = body(mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())).get("items");
        assertThat(items.get(0).get("fullName").asText()).isEqualTo("Rosnah");
        assertThat(items.get(1).get("fullName").asText()).as("higher score first within REVIEW").isEqualTo("Ahmad bin Ismail");
        assertThat(items.get(1).get("riskScore").asInt()).isGreaterThan(items.get(2).get("riskScore").asInt());
        assertThat(items.get(1).get("riskReasons").toString()).contains("Blood sugar rising");

        JsonNode clinicRisk = body(mvc.perform(get("/api/clinic/risk").header("Authorization", bearer(nurse.getId()))).andExpect(status().isOk()));
        assertThat(clinicRisk.get(0).get("fullName").asText()).isEqualTo("Ahmad bin Ismail");
    }
}
