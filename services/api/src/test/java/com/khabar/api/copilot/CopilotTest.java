package com.khabar.api.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
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
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.privacy.AiReceipts;
import com.khabar.api.reminders.DoseLog;
import com.khabar.api.reminders.DoseLogRepository;
import com.khabar.api.service.AgentClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ask Khabar, nurse call scripts, the visit pre-brief and the meal-photo check. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CopilotTest {

    static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ClinicStaffAccess staffAccess;
    @Autowired DoseLogRepository doses;
    @Autowired PatientReplyRepository replies;
    @Autowired AiReceipts receipts;
    @Autowired AuditLog auditLog;
    @MockBean AgentClientService agents;

    AppUser doctor, nurse, account;
    Patient ahmad, siti;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Tanya"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        nurse = users.save(new AppUser(UUID.randomUUID(), Role.NURSE, "Nurse Mei", clinic));
        staffAccess.grant(nurse, clinic, ClinicStaffRole.NURSE, doctor.getId(), clock.instant());
        account = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Ahmad", null));
        Patient a = new Patient(clinic, account, "Ahmad bin Ismail", "600921-08-5513", "012-555 0102", "ms");
        a.startFollowUp(LocalDate.now(clock).minusDays(10));
        ahmad = patients.save(a);
        Patient s = new Patient(clinic, null, "Siti Hajar", "700112-01-6046", "012-555 0105", "zh");
        s.startFollowUp(LocalDate.now(clock).minusDays(10));
        siti = patients.save(s);
        LocalDate today = LocalDate.now(clock);
        for (int d = 1; d <= 3; d++) {
            DoseLog dose = new DoseLog(ahmad.getId(), UUID.randomUUID(), today.minusDays(d), "morning", "Metformin", clock.instant());
            dose.answer("MISSED", clock.instant());
            doses.save(dose);
        }
    }

    private JsonNode ask(AppUser who, String question) throws Exception {
        return json.readTree(mvc.perform(post("/api/clinic/ask").header("Authorization", bearer(who.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("question", question))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void withoutAModelKhabarsRulesAnswerAndTheListedPatientsAreLogged() throws Exception {
        JsonNode answer = ask(doctor, "Who is missing doses?");
        assertThat(answer.get("source").asText()).isEqualTo("rules");
        assertThat(answer.get("patients")).hasSize(1);
        assertThat(answer.get("patients").get(0).get("fullName").asText()).isEqualTo("Ahmad bin Ismail");
        assertThat(answer.get("answer").asText()).contains("no AI model");
        assertThat(auditLog.forPatient(ahmad.getId())).anyMatch(e -> e.getAction() == AuditAction.VIEWED_RECORD);

        mvc.perform(post("/api/clinic/ask").header("Authorization", bearer(nurse.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theModelSeesCodesNotNamesAndItsCodesComeBackAsPatients() throws Exception {
        when(agents.askKhabar(anyString(), anyList())).thenAnswer(inv -> {
            List<Map<String, Object>> rows = inv.getArgument(1);
            String code = rows.stream().filter(r -> ((Map<String, Object>) r.get("doses14d")) != null).findFirst().orElseThrow().get("code").toString();
            return Map.of("answer", code + " has missed three doses this week.", "patients", List.of(Map.of("code", code, "why", "3 missed")));
        });
        patients.save(new Patient(ahmad.getClinic(), null, "Kumaravel a/l Rajan", "550101-07-1111", "012-555 0199", "ta"));
        JsonNode answer = ask(doctor, "How is Ahmad bin Ismail doing? And Ahmad's neighbour Kumaravel?");

        ArgumentCaptor<String> question = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<Map<String, Object>>> rows = ArgumentCaptor.forClass(List.class);
        verify(agents).askKhabar(question.capture(), rows.capture());
        assertThat(question.getValue()).doesNotContain("Ahmad").doesNotContain("Kumaravel").contains("[NAME]").matches("(?s).*P\\d+.*");
        assertThat(json.writeValueAsString(rows.getValue())).doesNotContain("Ahmad").doesNotContain("Siti").doesNotContain("012-555");
        assertThat(answer.get("source").asText()).isEqualTo("model");
        assertThat(answer.get("answer").asText()).startsWith("Ahmad bin Ismail has missed");
        assertThat(answer.get("patients").get(0).get("patientId").asText()).isEqualTo(ahmad.getId().toString());
        assertThat(receipts.latest(siti.getId(), 5)).filteredOn(r -> r.purpose().equals("ASK_KHABAR"))
                .as("another patient's receipt never holds the question").allMatch(r -> !r.sent().contains("Ahmad") && !r.sent().contains("How is"));
    }

    @Test
    void aCallScriptAsksAboutWhatTheRecordShowsAndEndsWithTheClinicsEmergencySentence() throws Exception {
        replies.save(new PatientReply(siti, "头晕了两天", clock.instant().minus(Duration.ofHours(2)), TriageLevel.REVIEW, null));
        JsonNode script = json.readTree(mvc.perform(post("/api/patients/{id}/call-script", ahmad.getId()).header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(script.get("source").asText()).isEqualTo("rules");
        assertThat(script.get("language").asText()).isEqualTo("ms");
        assertThat(script.toString()).contains("missed doses").contains("dos terlepas");
        assertThat(script.get("safety").get("english").asText()).contains("999");

        JsonNode zh = json.readTree(mvc.perform(post("/api/patients/{id}/call-script", siti.getId()).header("Authorization", bearer(nurse.getId())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(zh.get("lines").get(1).get("spoken").asText()).contains("信息");
        assertThat(receipts.latest(siti.getId(), 5)).anyMatch(r -> r.purpose().equals("CALL_SCRIPT") && !r.sent().contains("Siti"));
    }

    @Test
    void thePreBriefSummarisesWhatChangedSinceTheLastVisit() throws Exception {
        mvc.perform(get("/api/patients/{id}/prebrief", ahmad.getId()).header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value(org.hamcrest.Matchers.containsString("3 doses missed")))
                .andExpect(jsonPath("$.lines[0].kind").value("DOSES"));
        mvc.perform(get("/api/patients/{id}/prebrief", ahmad.getId()).header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMealPhotoGetsOnlyTheClinicsOwnTips() throws Exception {
        mvc.perform(post("/api/clinic/diet-tips").header("Authorization", bearer(doctor.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("category", "SWEET_DRINK", "ms", "Pilih air kosong.", "en", "Choose plain water."))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/clinic/diet-tips").header("Authorization", bearer(nurse.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("category", "FRIED", "ms", "x", "en", "x"))))
                .andExpect(status().isForbidden());
        when(agents.readMeal(any(), anyString())).thenReturn(Map.of("categories", List.of("RICE_NOODLES", "SWEET_DRINK", "UNKNOWN")));

        MockMultipartFile photo = new MockMultipartFile("image", "meal.jpg", "image/jpeg", JPEG);
        JsonNode reply = json.readTree(mvc.perform(multipart("/api/me/meal-photo").file(photo).param("consentConfirmed", "true")
                        .header("Authorization", bearer(account.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(reply.get("categories")).extracting(JsonNode::asText).containsExactly("RICE_NOODLES", "SWEET_DRINK");
        assertThat(reply.get("message").asText()).startsWith("🍽️ Nasihat klinik anda").contains("Minuman manis: Pilih air kosong.")
                .doesNotContain("Nasi / mi");
        assertThat(receipts.latest(ahmad.getId(), 5)).anyMatch(r -> r.purpose().equals("MEAL_PHOTO"));

        mvc.perform(multipart("/api/me/meal-photo").file(photo).param("consentConfirmed", "false").header("Authorization", bearer(account.getId())))
                .andExpect(status().isBadRequest());
    }
}
