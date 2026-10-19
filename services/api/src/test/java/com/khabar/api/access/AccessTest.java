package com.khabar.api.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.Role;
import com.khabar.api.messaging.Messenger;
import com.khabar.api.messaging.TelegramBotClient;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Voice replies, the simple screen, family groups, and caregivers acting for the patient. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ClinicStaffAccess staffAccess;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @Autowired DoseLogRepository doses;
    @Autowired PatientReplyRepository replies;
    @MockBean AgentClientService agents;
    @MockBean TelegramBotClient bot;

    AppUser account, caregiver, summaryOnly;
    Patient aminah;

    @BeforeEach
    void setUp() {
        clock.reset();
        when(bot.configured()).thenReturn(true);
        when(bot.sendMessage(anyLong(), anyString(), any())).thenReturn(Messenger.Result.ok("1"));
        when(bot.sendMessage(anyLong(), anyString())).thenReturn(Messenger.Result.ok("1"));
        Clinic clinic = clinics.save(new Clinic("Klinik Akses"));
        AppUser doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        account = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        aminah = patients.save(new Patient(clinic, account, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        caregiver = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Nurul", null));
        summaryOnly = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Ali", null));
        caregiverLinks.save(new CaregiverLink(aminah, caregiver, CaregiverScope.SUMMARY_AND_ALERTS));
        caregiverLinks.save(new CaregiverLink(aminah, summaryOnly, CaregiverScope.SUMMARY));
    }

    private DoseLog dose() {
        return doses.save(new DoseLog(aminah.getId(), UUID.randomUUID(), LocalDate.now(clock), "morning", "Metformin 500 mg", clock.instant()));
    }

    @Test
    void preferencesAreThePatientsOwn() throws Exception {
        mvc.perform(put("/api/me/preferences").header("Authorization", bearer(account.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"voiceFirst\":true,\"simpleMode\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simpleMode").value(true));
        mvc.perform(get("/api/me/preferences").header("Authorization", bearer(caregiver.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void aVoiceReplyComesBackAsTextToCheckBeforeSending() throws Exception {
        when(agents.transcribe(any(), anyString(), eq("ms"))).thenReturn(" Saya pening sikit ");
        MockMultipartFile audio = new MockMultipartFile("audio", "reply.webm", "audio/webm", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/me/voice-reply").file(audio).header("Authorization", bearer(account.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("Saya pening sikit"));
        assertThat(replies.findByPatientIdOrderByReceivedAtAsc(aminah.getId())).as("nothing is sent until the patient confirms").isEmpty();
    }

    @Test
    void aCaregiverWithAlertConsentAnswersDosesAndCheckInsAsThemselves() throws Exception {
        DoseLog d = dose();
        mvc.perform(get("/api/patients/{id}/doses-waiting", aminah.getId()).header("Authorization", bearer(caregiver.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(d.getId().toString()));
        mvc.perform(post("/api/patients/{id}/doses/{dose}", aminah.getId(), d.getId()).header("Authorization", bearer(caregiver.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"TAKEN\"}"))
                .andExpect(status().isOk());
        assertThat(doses.findById(d.getId()).orElseThrow().getAnsweredVia()).isEqualTo("CAREGIVER");

        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "ok"));
        mvc.perform(post("/api/patients/{id}/replies-on-behalf", aminah.getId()).header("Authorization", bearer(caregiver.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Mak sihat hari ini\"}"))
                .andExpect(status().isOk());
        assertThat(replies.findByPatientIdOrderByReceivedAtAsc(aminah.getId()).get(0).getText()).isEqualTo("[Nurul, caregiver] Mak sihat hari ini");

        mvc.perform(post("/api/patients/{id}/replies-on-behalf", aminah.getId()).header("Authorization", bearer(summaryOnly.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patients/{id}/slots", aminah.getId()).header("Authorization", bearer(caregiver.getId())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/patients/{id}/slots", aminah.getId()).header("Authorization", bearer(summaryOnly.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aFamilyGroupLinkedWithACodeCanAnswerDosesButNotSeeAnythingElse() throws Exception {
        JsonNode code = json.readTree(mvc.perform(post("/api/me/family-group/link-code").header("Authorization", bearer(account.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        long group = -100777000L - System.nanoTime() % 1000;
        telegram(Map.of("message", Map.of("message_id", 1, "text", "/family " + code.get("code").asText(),
                "chat", Map.of("id", group, "type", "group"), "from", Map.of("id", 5))));
        verify(bot).sendMessage(eq(group), contains("Aminah"));
        mvc.perform(get("/api/me/family-group").header("Authorization", bearer(account.getId())))
                .andExpect(jsonPath("$.linked").value(true));

        DoseLog d = dose();
        telegram(Map.of("callback_query", Map.of("id", "cb1", "data", "d:" + d.getId() + ":M", "from", Map.of("id", 99),
                "message", Map.of("message_id", 8, "chat", Map.of("id", group, "type", "group")))));
        DoseLog answered = doses.findById(d.getId()).orElseThrow();
        assertThat(answered.getResponse()).isEqualTo("MISSED");
        assertThat(answered.getAnsweredVia()).isEqualTo("FAMILY");

        // Text in the group is not a reply to the clinic.
        telegram(Map.of("message", Map.of("message_id", 2, "text", "Mak sakit dada", "chat", Map.of("id", group, "type", "group"), "from", Map.of("id", 5))));
        assertThat(replies.findByPatientIdOrderByReceivedAtAsc(aminah.getId())).isEmpty();
    }

    private void telegram(Map<String, Object> update) throws Exception {
        mvc.perform(post("/api/webhooks/telegram").header("X-Telegram-Bot-Api-Secret-Token", "test-telegram-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk());
    }
}
