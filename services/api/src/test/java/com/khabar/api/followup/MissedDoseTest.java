package com.khabar.api.followup;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.messaging.CheckInSender;
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

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** "Call these patients today" ranks red flags first, then missed doses, then patients who went quiet. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MissedDoseTest {

    @Autowired MockMvc mvc;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ApprovedAnswerRepository answers;
    @Autowired CheckInPlanner planner;
    @Autowired CheckInSender sender;
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

    ResultActions aminahReplies(String text) throws Exception {
        return mvc.perform(post("/api/followup/replies").header("Authorization", bearer(aminahAccount.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"" + text + "\"}"));
    }

    ResultActions callList() throws Exception {
        return mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())));
    }

    @Test
    void aMissedDoseGetsTheApprovedAnswerAndStillShowsOnTheCallList() throws Exception {
        ApprovedAnswer missed = answers.save(new ApprovedAnswer(doctor.getClinic(), "Missed a dose", List.of("lupa makan ubat"),
                Map.of("ms", "Ambil sebaik sahaja teringat."), doctor, clock.instant()));
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review", "missed_dose", true));
        when(agents.matchAnswer(anyString(), anyList())).thenReturn(missed.getId().toString());

        aminahReplies("Semalam lupa makan ubat").andExpect(jsonPath("$.answer").value("Ambil sebaik sahaja teringat."));

        callList().andExpect(jsonPath("$.items[0].fullName").value("Aminah binti Yusof"))
                .andExpect(jsonPath("$.items[0].reason").value("MISSED_DOSE"))
                .andExpect(jsonPath("$.items[0].level").value("REVIEW"));
    }

    @Test
    void aCheerfulReplyThatAdmitsAMissedDoseIsStillListed() throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "ok", "matched", "ok", "missed_dose", true));

        aminahReplies("OK je, cuma lupa makan ubat semalam");

        callList().andExpect(jsonPath("$.items[0].reason").value("MISSED_DOSE"))
                .andExpect(jsonPath("$.items[0].level").value("REVIEW"))
                .andExpect(jsonPath("$.counts.review").value(1));
    }

    @Test
    void missedDosesRankAbovePatientsWhoWentQuiet() throws Exception {
        LocalDate today = LocalDate.now(clock);
        planner.startFollowUp(tan, null, today.minusDays(1), false);
        sender.sendDue();
        clock.advance(Duration.ofDays(3));
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review", "missed_dose", true));

        aminahReplies("Lupa makan ubat");

        callList().andExpect(jsonPath("$.items[0].reason").value("MISSED_DOSE"))
                .andExpect(jsonPath("$.items[1].reason").value("NO_REPLY"));
    }

    @Test
    void callingThePatientClearsTheMissedDose() throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review", "missed_dose", true));
        aminahReplies("Lupa makan ubat");

        mvc.perform(post("/api/clinic/call-list/{id}/called", aminah.getId()).header("Authorization", bearer(doctor.getId())));

        callList().andExpect(jsonPath("$.items.length()").value(0));
    }
}
