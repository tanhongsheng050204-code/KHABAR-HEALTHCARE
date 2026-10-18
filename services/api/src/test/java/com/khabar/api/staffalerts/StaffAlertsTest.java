package com.khabar.api.staffalerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.FollowUpCaseRepository;
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

import java.time.Duration;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Counts-only staff alerts and the morning briefing, linked to a staff chat with a one-time code. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAlertsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ClinicStaffAccess staffAccess;
    @Autowired StaffAlerts alerts;
    @Autowired StaffAlertLogRepository logs;
    @Autowired FollowUpCaseRepository cases;
    @MockBean AgentClientService agents;

    Clinic clinic;
    AppUser doctor, nurse, aminahAccount;
    Patient aminah;

    @BeforeEach
    void setUp() {
        clock.reset();
        clinic = clinics.save(new Clinic("Klinik Amaran"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        nurse = users.save(new AppUser(UUID.randomUUID(), Role.NURSE, "Nurse Mei", clinic));
        staffAccess.grant(nurse, clinic, ClinicStaffRole.NURSE, doctor.getId(), clock.instant());
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        Patient p = new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms");
        p.startFollowUp(LocalDate.now(clock).minusDays(3));
        aminah = patients.save(p);
    }

    @Test
    void aOneTimeCodeSentFromAGroupLinksItAsTheStaffChat() throws Exception {
        mvc.perform(post("/api/clinic/staff-chat/link-code").header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isForbidden());
        String code = linkCode();

        telegram(Map.of("message", Map.of("message_id", 7, "text", "/staff@KhabarApp_Bot " + code,
                "chat", Map.of("id", -100123456, "type", "supergroup"), "from", Map.of("id", 42))));

        mvc.perform(get("/api/clinic/staff-chat").header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.pendingCode").value(false));
    }

    @Test
    void anExpiredCodeLinksNothing() throws Exception {
        String code = linkCode();
        clock.advance(Duration.ofMinutes(31));
        assertThat(alerts.linkFromTelegram(code, "-100999")).isEmpty();
        assertThat(alerts.channelFor(clinic.getId()).getChatId()).isNull();
    }

    @Test
    void anAlertCountsWithoutNamingAnyoneAndIsNotRepeated() throws Exception {
        switchOn(null);
        urgentReply();
        long casesBefore = cases.count();

        alerts.checkAll();

        List<StaffAlertLog> sent = logs.findByClinicIdOrderByIdDesc(clinic.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(sent).hasSize(1);
        StaffAlertLog alert = sent.get(0);
        assertThat(alert.getKind()).isEqualTo("ALERT");
        assertThat(alert.getText()).contains("Klinik Amaran").contains("1 patient needs a call").contains("1 urgent")
                .doesNotContain("Aminah").doesNotContain("sakit");
        // No chat is linked and Telegram is not configured in tests, so it is recorded, not sent.
        assertThat(alert.getOutcome()).isEqualTo("NOT_SENT");
        assertThat(cases.count()).as("a background count must not open cases").isEqualTo(casesBefore);

        alerts.checkAll();
        assertThat(logs.findByClinicIdOrderByIdDesc(clinic.getId(), org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(1);
    }

    @Test
    void theMorningBriefingGoesOutOncePerDay() throws Exception {
        switchOn(0);
        alerts.checkAll();
        alerts.checkAll();
        List<StaffAlertLog> sent = logs.findByClinicIdOrderByIdDesc(clinic.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(sent).extracting(StaffAlertLog::getKind).containsExactly("BRIEFING");
        assertThat(sent.get(0).getText()).startsWith("Good morning, Klinik Amaran.").contains("Call list: 0 patients")
                .contains("Nobody is rostered");
    }

    @Test
    void nothingIsCheckedForAClinicThatLeftAlertsOff() throws Exception {
        urgentReply();
        alerts.checkAll();
        assertThat(logs.findByClinicIdOrderByIdDesc(clinic.getId(), org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void managersCanSendATestAndPreviewTheBriefing() throws Exception {
        mvc.perform(post("/api/clinic/staff-chat/test").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TEST"))
                .andExpect(jsonPath("$.outcome").value("NOT_SENT"));
        mvc.perform(get("/api/clinic/staff-chat/briefing").header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.startsWith("Good morning")));
        mvc.perform(post("/api/clinic/staff-chat/test").header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isForbidden());
    }

    private String linkCode() throws Exception {
        String body = mvc.perform(post("/api/clinic/staff-chat/link-code").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode code = json.readTree(body);
        assertThat(code.get("command").asText()).startsWith("/staff KB-");
        return code.get("code").asText();
    }

    private void switchOn(Integer briefingHour) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("alertsOn", true);
        body.put("briefingHour", briefingHour);
        mvc.perform(put("/api/clinic/staff-chat").header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertsOn").value(true));
    }

    private void urgentReply() throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "red", "matched", "sakit dada"));
        mvc.perform(post("/api/followup/replies").header("Authorization", bearer(aminahAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", "sakit dada sejak pagi"))))
                .andExpect(status().isOk());
    }

    private void telegram(Map<String, Object> update) throws Exception {
        mvc.perform(post("/api/webhooks/telegram").header("X-Telegram-Bot-Api-Secret-Token", "test-telegram-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk());
    }

    @Test
    void theHandoverCountsOpenCasesAndOwnersButNeverPatients() throws Exception {
        urgentReply();
        String list = mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andReturn().getResponse().getContentAsString();
        String caseId = json.readTree(list).get("items").get(0).get("followUpCase").get("id").asText();
        mvc.perform(post("/api/clinic/cases/{id}/assign", caseId).header("Authorization", bearer(nurse.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        String text = json.readTree(mvc.perform(get("/api/clinic/staff-chat/handover").header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("text").asText();
        assertThat(text).startsWith("Handover · Klinik Amaran").contains("Open cases: 1 (1 urgent)").contains("No owner yet: 0")
                .contains("Owners: Nurse Mei 1").doesNotContain("Aminah");

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("alertsOn", true);
        body.put("handoverHour", 0);
        mvc.perform(put("/api/clinic/staff-chat").header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(jsonPath("$.handoverHour").value(0));
        alerts.checkAll();
        alerts.checkAll();
        assertThat(logs.findByClinicIdOrderByIdDesc(clinic.getId(), org.springframework.data.domain.PageRequest.of(0, 10)))
                .extracting(StaffAlertLog::getKind).filteredOn("HANDOVER"::equals).hasSize(1);
    }
}
