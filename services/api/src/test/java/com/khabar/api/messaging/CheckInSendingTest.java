package com.khabar.api.messaging;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CheckIn;
import com.khabar.api.followup.CheckInPlanner;
import com.khabar.api.followup.CheckInRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckInSendingTest {

    @Autowired MockMvc mvc;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CheckInPlanner planner;
    @Autowired CheckInRepository checkIns;
    @Autowired CheckInSender sender;
    @Autowired OutboundMessageRepository outbox;
    @MockBean AgentClientService agents;

    AppUser doctor, aminahAccount;
    Patient aminah, tan;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        tan = patients.save(new Patient(clinic, null, "Tan Kok Hoe", "540101-07-1234", "016-222 3333", "zh"));
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    List<OutboundMessage> sentTo(Patient p) {
        return outbox.findAll().stream().filter(m -> m.getPatientId().equals(p.getId())).toList();
    }

    @Test
    void aDueCheckInIsSentInThePatientsLanguage() {
        planner.startFollowUp(aminah, null, today().minusDays(1), false);
        planner.startFollowUp(tan, null, today().minusDays(1), false);

        sender.sendDue();

        assertThat(sentTo(aminah)).singleElement().satisfies(m -> {
            assertThat(m.getText()).startsWith("Apa khabar hari ini?");
            assertThat(m.getKind()).isEqualTo("CHECK_IN");
        });
        assertThat(sentTo(tan)).singleElement().satisfies(m -> assertThat(m.getText()).startsWith("今天感觉怎么样"));
        assertThat(checkIns.findByPatientIdOrderByDueDate(aminah.getId())).extracting(CheckIn::getStatus)
                .containsExactly(CheckIn.Status.SENT, CheckIn.Status.PENDING, CheckIn.Status.PENDING, CheckIn.Status.PENDING, CheckIn.Status.PENDING);
    }

    @Test
    void checkInsThatAreNotDueYetWait() {
        planner.startFollowUp(aminah, null, today(), false);
        sender.sendDue();
        assertThat(sentTo(aminah)).isEmpty();
    }

    @Test
    void duringFastingTheCheckInAsksAboutShakinessBeforeBerbuka() {
        planner.startFollowUp(aminah, null, today().minusDays(1), true);
        sender.sendDue();
        assertThat(sentTo(aminah)).singleElement().satisfies(m -> assertThat(m.getText()).contains("berbuka"));
    }

    @Test
    void fastForwardingTheClockSendsLaterCheckIns() {
        planner.startFollowUp(aminah, null, today(), false);
        clock.advance(Duration.ofDays(7));
        sender.sendDue();
        // days 1, 3 and 7 are all due by now; each is sent once
        assertThat(sentTo(aminah)).hasSize(3);
        sender.sendDue();
        assertThat(sentTo(aminah)).hasSize(3);
    }

    @Test
    void aPatientWhoStopsReplyingAppearsOnTheCallList() throws Exception {
        planner.startFollowUp(aminah, null, today().minusDays(1), false);
        sender.sendDue();
        clock.advance(Duration.ofDays(3));

        mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andExpect(jsonPath("$.items[0].fullName").value("Aminah binti Yusof"))
                .andExpect(jsonPath("$.items[0].level").value("REVIEW"))
                .andExpect(jsonPath("$.items[0].reason").value("NO_REPLY"));
    }

    @Test
    void aReplyAnswersTheCheckInAndClearsTheNoReplyFlag() throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "ok", "matched", "sihat"));
        planner.startFollowUp(aminah, null, today().minusDays(1), false);
        sender.sendDue();
        clock.advance(Duration.ofDays(3));

        mvc.perform(post("/api/followup/replies").header("Authorization", bearer(aminahAccount.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Sihat, dah makan ubat\"}"));

        mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(checkIns.findByPatientIdOrderByDueDate(aminah.getId()).get(0).getStatus()).isEqualTo(CheckIn.Status.ANSWERED);
    }
}
