package com.khabar.api.followup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.messaging.OutboundMessage;
import com.khabar.api.messaging.OutboundMessageRepository;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos.AnswerOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApprovedAnswersTest {

    @Test
    void urgentPatientMessagesDoNotPromiseThatStaffHaveSeenTheReplyOrWillCall() {
        Map<String, String> notSeen = Map.of(
                "ms", "mungkin belum membacanya",
                "en", "may not have seen it yet",
                "zh", "可能还没有看到",
                "ta", "இன்னும் பார்க்காமல் இருக்கலாம்");
        Map.of(
                "ms", new String[]{"akan menghubungi", "sudah dimaklumkan"},
                "en", new String[]{"will contact you", "has been told"},
                "zh", new String[]{"会联系您", "已收到通知"},
                "ta", new String[]{"உங்களைத் தொடர்புகொள்வார்கள்", "தெரிவிக்கப்பட்டது"}
        ).forEach((language, promises) -> {
            String text = com.khabar.api.messaging.PatientMessages.urgentText(language);
            assertThat(text).contains("999");
            assertThat(text).contains(notSeen.get(language));
            assertThat(text).doesNotContain(promises);
        });
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired OutboundMessageRepository outbox;
    @MockBean AgentClientService agents;

    AppUser doctor, doctorElsewhere, aminahAccount;
    Patient aminah;

    static final Map<String, Object> MISSED_DOSE = Map.of(
            "title", "Missed a dose",
            "triggers", List.of("lupa makan ubat", "forgot"),
            "texts", Map.of("ms", "Ambil sebaik sahaja teringat. Jangan ambil dua dos sekali.", "en", "Take it as soon as you remember. Never take two doses at once."));

    @BeforeEach
    void setUp() {
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
    }

    ResultActions approve(AppUser as, Map<String, Object> answer) throws Exception {
        return mvc.perform(post("/api/clinic/answers").header("Authorization", bearer(as.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(answer)));
    }

    String approveMissedDose() throws Exception {
        String body = approve(doctor, MISSED_DOSE).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    ResultActions reply(String text) throws Exception {
        return mvc.perform(post("/api/followup/replies").header("Authorization", bearer(aminahAccount.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", text))));
    }

    List<OutboundMessage> sentToAminah() {
        return outbox.findAll().stream().filter(m -> m.getPatientId().equals(aminah.getId())).toList();
    }

    ResultActions callList() throws Exception {
        return mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())));
    }

    @Test
    void aDoctorApprovesAnswersForTheirOwnClinic() throws Exception {
        approveMissedDose();

        mvc.perform(get("/api/clinic/answers").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Missed a dose"))
                .andExpect(jsonPath("$[0].triggers[1]").value("forgot"))
                .andExpect(jsonPath("$[0].texts.ms").value("Ambil sebaik sahaja teringat. Jangan ambil dua dos sekali."))
                .andExpect(jsonPath("$[0].approvedBy").value("Dr Priya"));
        mvc.perform(get("/api/clinic/answers").header("Authorization", bearer(doctorElsewhere.getId())))
                .andExpect(jsonPath("$.length()").value(0));
        approve(aminahAccount, MISSED_DOSE).andExpect(status().isForbidden());
    }

    @Test
    void anAnswerNeedsATriggerAndATextInASupportedLanguage() throws Exception {
        approve(doctor, Map.of("title", "x", "triggers", List.of(), "texts", Map.of("en", "Hello"))).andExpect(status().isBadRequest());
        approve(doctor, Map.of("title", "x", "triggers", List.of("forgot"), "texts", Map.of("fr", "Bonjour"))).andExpect(status().isBadRequest());
    }

    @Test
    void aQuestionWithAnApprovedAnswerGetsTheDoctorsOwnWordsAndLeavesTheCallList() throws Exception {
        String answerId = approveMissedDose();
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review"));
        when(agents.matchAnswer(anyString(), anyList())).thenReturn(answerId);

        reply("Semalam saya lupa makan ubat malam").andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("REVIEW"))
                .andExpect(jsonPath("$.answer").value("Ambil sebaik sahaja teringat. Jangan ambil dua dos sekali."))
                .andExpect(jsonPath("$.message").value("Ambil sebaik sahaja teringat. Jangan ambil dua dos sekali."));

        assertThat(sentToAminah()).singleElement().satisfies(m -> {
            assertThat(m.getKind()).isEqualTo("ANSWER");
            assertThat(m.getText()).isEqualTo("Ambil sebaik sahaja teringat. Jangan ambil dua dos sekali.");
        });
        callList().andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void aRedFlagIsNeverAnsweredAutomaticallyButThePatientIsToldWhatToDoNow() throws Exception {
        approveMissedDose();
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "red", "matched", "sakit dada"));

        reply("Sakit dada, lupa makan ubat").andExpect(jsonPath("$.level").value("RED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("999")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("mungkin belum membacanya")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("will contact you"))));

        verify(agents, never()).matchAnswer(anyString(), anyList());
        assertThat(sentToAminah()).singleElement().satisfies(m -> {
            assertThat(m.getKind()).isEqualTo("SAFETY");
            assertThat(m.getText()).contains("999");
            assertThat(m.getText()).contains("mungkin belum membacanya");
            assertThat(m.getText()).doesNotContain("will contact you");
        });
        callList().andExpect(jsonPath("$.items[0].level").value("RED"));
    }

    @Test
    void aQuestionWithNoApprovedAnswerGoesToAPersonAndThePatientIsToldSo() throws Exception {
        approveMissedDose();
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review"));

        // The app shows the patient the same acknowledgement their phone gets, never a blank message
        reply("Boleh makan durian?").andExpect(jsonPath("$.answer").doesNotExist())
                .andExpect(jsonPath("$.message").value(com.khabar.api.messaging.PatientMessages.acknowledgementText("ms", TriageLevel.REVIEW)))
                // Nobody has read it yet, and the word lists miss many ways of describing an emergency
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("999")));

        assertThat(sentToAminah()).singleElement().satisfies(m -> assertThat(m.getKind()).isEqualTo("NOTICE"));
        callList().andExpect(jsonPath("$.items[0].level").value("REVIEW"));
    }

    @Test
    void aReassuringReplyGetsAPlainThankYou() throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "ok", "matched", "sihat"));

        reply("Sihat, dah makan ubat").andExpect(jsonPath("$.level").value("OK"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("999"))));
    }

    @Test
    void retiredAnswersAreNoLongerOffered() throws Exception {
        String answerId = approveMissedDose();
        mvc.perform(delete("/api/clinic/answers/{id}", answerId).header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk());
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review"));

        reply("Saya lupa makan ubat");

        verify(agents, never()).matchAnswer(anyString(), anyList());
    }

    @Test
    void onlyAnswersWrittenInThePatientsLanguageOrEnglishAreOffered() throws Exception {
        approveMissedDose();
        approve(doctor, Map.of("title", "Chinese only", "triggers", List.of("药"), "texts", Map.of("zh", "请回诊所。")));
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review"));

        reply("Saya lupa makan ubat");

        verify(agents).matchAnswer(eq("Saya lupa makan ubat"), argThat((List<AnswerOption> options) ->
                options.size() == 1 && options.get(0).title().equals("Missed a dose")));
    }
}
