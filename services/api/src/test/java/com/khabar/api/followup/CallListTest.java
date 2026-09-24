package com.khabar.api.followup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CallListTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired PatientReplyRepository replies;
    @MockBean AgentClientService agents;

    AppUser doctor, doctorElsewhere, aminahAccount, rosnahAccount, tanAccount, strangerAccount;
    Patient aminah, rosnah, tan, stranger;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        rosnahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Rosnah", null));
        tanAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Tan", null));
        strangerAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Stranger", null));

        aminah = followedUp(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"), 2);
        rosnah = followedUp(new Patient(clinic, rosnahAccount, "Rosnah binti Ahmad", "620505-14-2222", "013-111 2222", "ms"), 5);
        tan = followedUp(new Patient(clinic, tanAccount, "Tan Kok Hoe", "540101-07-1234", "016-222 3333", "zh"), 8);
        stranger = followedUp(new Patient(other, strangerAccount, "Other Clinic Patient", "700101-01-0001", "017-000 0000", "en"), 1);
    }

    Patient followedUp(Patient p, int daysAgo) {
        p.startFollowUp(LocalDate.now().minusDays(daysAgo));
        return patients.save(p);
    }

    void triageSays(String level, String matched) {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", level, "matched", matched));
    }

    ResultActions reply(AppUser as, String text) throws Exception {
        return mvc.perform(post("/api/followup/replies").header("Authorization", bearer(as.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"" + text + "\"}"));
    }

    ResultActions callList(AppUser as) throws Exception {
        return mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(as.getId())));
    }

    String snapshotAt(AppUser as) throws Exception {
        String body = callList(as).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("snapshotAt").asText();
    }

    ResultActions recordContact(AppUser as, Patient patient, String observedThrough) throws Exception {
        return mvc.perform(post("/api/clinic/call-list/{id}/called", patient.getId())
                .header("Authorization", bearer(as.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("observedThrough", observedThrough))));
    }

    @Test
    void aPatientsReplyIsTriagedAndStored() throws Exception {
        triageSays("watch", "pening");
        reply(aminahAccount, "Pening dan berpeluh").andExpect(status().isOk()).andExpect(jsonPath("$.level").value("WATCH"));
        assertThat(replies.findAll()).anyMatch(r -> r.getPatient().getId().equals(aminah.getId()) && r.getLevel() == TriageLevel.WATCH);
    }

    @Test
    void theReplyIsStrippedOfTheirIdentityBeforeTriage() throws Exception {
        triageSays("watch", "pening");
        reply(aminahAccount, "Aminah sini, IC 590312-10-5566. Pening.").andExpect(status().isOk());
        verify(agents).triageReply(org.mockito.ArgumentMatchers.argThat(text ->
                !text.contains("Aminah") && !text.contains("590312-10-5566") && text.contains("Pening")),
                org.mockito.ArgumentMatchers.eq(aminah.getGraphId().toString()));
    }

    @Test
    void whenTheAgentsServiceIsDownTheReplyStillGoesToAPerson() throws Exception {
        when(agents.triageReply(anyString(), any())).thenThrow(new IllegalStateException("agents down"));
        reply(aminahAccount, "Pening").andExpect(status().isOk()).andExpect(jsonPath("$.level").value("REVIEW"));
        callList(doctor).andExpect(jsonPath("$.items[0].patientId").value(aminah.getId().toString()))
                .andExpect(jsonPath("$.items[0].level").value("REVIEW"));
    }

    @Test
    void whenNobodyCouldCheckTheReplyThePatientIsStillToldWhatToDoInAnEmergency() throws Exception {
        when(agents.triageReply(anyString(), any())).thenThrow(new IllegalStateException("agents down"));
        reply(aminahAccount, "Sakit dada").andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("REVIEW"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Klinik akan semak mesej anda"),
                        org.hamcrest.Matchers.containsString("999"))));
    }

    @Test
    void onlyPatientsCanSendReplies() throws Exception {
        reply(doctor, "Pening").andExpect(status().isForbidden());
    }

    @Test
    void callListShowsThisClinicsUrgentPatientsMostUrgentFirst() throws Exception {
        triageSays("watch", "pening");
        reply(aminahAccount, "Pening dan berpeluh");
        triageSays("red", "sakit dada");
        reply(rosnahAccount, "Sakit dada sejak pagi");
        triageSays("ok", "sihat");
        reply(tanAccount, "Sihat");
        triageSays("red", "sakit dada");
        reply(strangerAccount, "Sakit dada");

        callList(doctor).andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotAt").exists())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].fullName").value("Rosnah binti Ahmad"))
                .andExpect(jsonPath("$.items[0].level").value("RED"))
                .andExpect(jsonPath("$.items[0].latestReply").value("Sakit dada sejak pagi"))
                .andExpect(jsonPath("$.items[0].followUpDay").value(5))
                .andExpect(jsonPath("$.items[1].fullName").value("Aminah binti Yusof"))
                .andExpect(jsonPath("$.counts.red").value(1))
                .andExpect(jsonPath("$.counts.watch").value(1))
                .andExpect(jsonPath("$.patientsInFollowUp").value(3));
    }

    @Test
    void aPatientAppearsOnceAtTheirMostUrgentLevel() throws Exception {
        triageSays("watch", "pening");
        reply(aminahAccount, "Pening");
        triageSays("red", "sakit dada");
        reply(aminahAccount, "Sakit dada pula");
        triageSays("watch", "pening");
        reply(aminahAccount, "Masih pening");

        callList(doctor).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].level").value("RED"))
                .andExpect(jsonPath("$.items[0].unhandledReplies").value(3));
    }

    @Test
    void theCallListIsForDoctorsOnly() throws Exception {
        callList(aminahAccount).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void markingAPatientAsCalledClearsThemAndShowsInTheirAccessLog() throws Exception {
        triageSays("red", "sakit dada");
        reply(rosnahAccount, "Sakit dada");

        recordContact(doctor, rosnah, snapshotAt(doctor))
                .andExpect(status().isOk());

        callList(doctor).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/patients/{id}/access-log", rosnah.getId()).header("Authorization", bearer(rosnahAccount.getId())))
                .andExpect(jsonPath("$[0].action").value("CALLED_ABOUT_REPLY"))
                .andExpect(jsonPath("$[0].actor").value("Dr Priya · Klinik Dr Priya"));
    }

    @Test
    void contactOnlyClosesItemsThatWereInTheQueueSnapshot() throws Exception {
        triageSays("red", "sakit dada");
        reply(rosnahAccount, "Sakit dada");
        String snapshot = snapshotAt(doctor);

        clock.advance(Duration.ofSeconds(2));
        triageSays("watch", "pening");
        reply(rosnahAccount, "Pening selepas itu");

        recordContact(doctor, rosnah, snapshot).andExpect(status().isOk())
                .andExpect(jsonPath("$.handledReplies").value(1));

        callList(doctor).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].latestReply").value("Pening selepas itu"))
                .andExpect(jsonPath("$.items[0].unhandledReplies").value(1));
    }

    @Test
    void contactCannotUseATimestampLaterThanTheServerClock() throws Exception {
        String future = clock.instant().plusSeconds(60).toString();
        recordContact(doctor, rosnah, future).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void aDoctorAtAnotherClinicCannotMarkThePatientAsCalled() throws Exception {
        recordContact(doctorElsewhere, rosnah, snapshotAt(doctorElsewhere))
                .andExpect(status().isForbidden());
    }
}
