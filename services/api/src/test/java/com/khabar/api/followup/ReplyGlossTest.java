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
import com.khabar.api.privacy.AiReceipts;
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
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff see an English machine translation beside a reply in another language, never instead of it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReplyGlossTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ClinicStaffAccess staffAccess;
    @Autowired AiReceipts receipts;
    @MockBean AgentClientService agents;

    AppUser doctor;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Terjemah"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
    }

    private Patient patient(String language) {
        AppUser account = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "P", null));
        return patients.save(new Patient(doctor.getClinic(), account, "Pesakit " + language, "590312-10-55" + (int) (Math.random() * 90 + 10),
                "012-345 67" + (int) (Math.random() * 90 + 10), language));
    }

    private void reply(Patient p, String text) throws Exception {
        mvc.perform(post("/api/followup/replies").header("Authorization", bearer(p.getAccount().getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", text))))
                .andExpect(status().isOk());
    }

    @Test
    void aMalayReplyCarriesAnEnglishGlossOnTheCallList() throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review"));
        when(agents.glossReply(anyString())).thenReturn(Map.of("english", "I have been dizzy since this morning"));
        Patient aminah = patient("ms");
        reply(aminah, "Saya pening sejak pagi");

        JsonNode item = json.readTree(mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("items").get(0);
        assertThat(item.get("latestReply").asText()).isEqualTo("Saya pening sejak pagi");
        assertThat(item.get("latestEnglish").asText()).isEqualTo("I have been dizzy since this morning");
        assertThat(receipts.latest(aminah.getId(), 10)).anyMatch(r -> r.purpose().equals("REPLY_GLOSS"));
    }

    @Test
    void anEnglishSpeakerIsNotTranslatedAndAFailedGlossLosesNothing() throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review"));
        Patient english = patient("en");
        reply(english, "I feel dizzy");
        verify(agents, never()).glossReply(anyString());

        when(agents.glossReply(anyString())).thenThrow(new RuntimeException("agents down"));
        reply(patient("zh"), "我头晕");
        JsonNode items = json.readTree(mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("items");
        assertThat(items).hasSize(2).allMatch(i -> i.get("latestEnglish").isNull());
    }
}
