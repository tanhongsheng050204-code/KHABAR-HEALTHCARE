package com.khabar.api.messaging;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CheckInPlanner;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The no-reply sequence: one gentle re-ask after a day, then the caregiver with alert consent sees the silence. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoReplySequenceTest {

    @Autowired MockMvc mvc;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @Autowired CheckInPlanner planner;
    @Autowired CheckInSender sender;
    @Autowired OutboundMessageRepository outbox;
    @MockBean AgentClientService agents;

    Patient aminah;
    AppUser nurul, summaryOnly;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Senyap"));
        AppUser account = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        aminah = patients.save(new Patient(clinic, account, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        nurul = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Nurul", null));
        summaryOnly = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Hafiz", null));
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));
        caregiverLinks.save(new CaregiverLink(aminah, summaryOnly, CaregiverScope.SUMMARY));
    }

    List<OutboundMessage> sentTo(Patient p) {
        return outbox.findAll().stream().filter(m -> m.getPatientId().equals(p.getId())).toList();
    }

    @Test
    void anUnansweredCheckInGetsOneGentleReAskAfterADay() {
        planner.startFollowUp(aminah, null, LocalDate.now(clock).minusDays(1), false);
        sender.sendDue();
        sender.sendReminders();
        assertThat(sentTo(aminah)).hasSize(1);

        clock.advance(Duration.ofHours(25));
        sender.sendReminders();
        sender.sendReminders();

        assertThat(sentTo(aminah)).hasSize(2);
        assertThat(sentTo(aminah).get(1).getText()).startsWith("Kami belum dengar khabar anda sejak semalam.");
    }

    @Test
    void aCaregiverWithAlertConsentSeesTwoDaysOfSilence() throws Exception {
        planner.startFollowUp(aminah, null, LocalDate.now(clock).minusDays(1), false);
        sender.sendDue();
        mvc.perform(get("/api/patients/{id}/quiet", aminah.getId()).header("Authorization", bearer(nurul.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiet").value(false));

        clock.advance(Duration.ofHours(49));
        mvc.perform(get("/api/patients/{id}/quiet", aminah.getId()).header("Authorization", bearer(nurul.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiet").value(true))
                .andExpect(jsonPath("$.dayNumber").value(1));
        mvc.perform(get("/api/patients/{id}/quiet", aminah.getId()).header("Authorization", bearer(summaryOnly.getId())))
                .andExpect(status().isForbidden());
    }
}
