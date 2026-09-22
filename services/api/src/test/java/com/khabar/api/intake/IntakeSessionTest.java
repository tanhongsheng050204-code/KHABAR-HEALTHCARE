package com.khabar.api.intake;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.khabar.api.service.AgentDtos.DraftedReport;
import com.khabar.api.service.AgentDtos.IntakeAnswer;
import com.khabar.api.service.AgentDtos.IntakeFlag;
import com.khabar.api.service.AgentDtos.MedicineMention;
import com.khabar.api.service.AgentDtos.PreVisitReport;
import com.khabar.api.service.AgentDtos.SafetyCheckResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntakeSessionTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @MockBean AgentClientService agents;

    AppUser doctor, doctorElsewhere, aminahAccount, nurul;
    Patient aminah;

    static final String CHAT = """
            {"messages": [
              {"role": "assistant", "content": "Apa sebab datang ke klinik hari ini?"},
              {"role": "user", "content": "Saya Aminah, IC 590312-10-5566. Pening sejak 3 hari."},
              {"role": "assistant", "content": "Apa ubat yang sedang diambil?"},
              {"role": "user", "content": "Brand A 500mg dari GP dan jus peria"}
            ]}
            """;

    static final PreVisitReport REPORT = new PreVisitReport(
            "Pening sejak 3 hari.",
            List.of(new IntakeAnswer("reason", "Apa sebab datang ke klinik hari ini?", "Pening sejak 3 hari.")),
            List.of(new MedicineMention("Brand A 500mg", "metformin")),
            List.of("peria"),
            List.of("penicillin"),
            List.of("jamu"),
            List.of(new IntakeFlag("watch", "pening")));

    @BeforeEach
    void setUp() {
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        nurul = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Nurul", null));
        aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));
    }

    void agentSays(boolean complete) {
        when(agents.processIntake(anyString(), anyString(), anyList()))
                .thenReturn(Map.of("next_question", complete ? "Terima kasih." : "Ada alahan ubat?", "is_complete", complete));
    }

    ResultActions chat() throws Exception {
        return mvc.perform(post("/api/intake/chat").header("Authorization", bearer(aminahAccount.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(CHAT));
    }

    ResultActions readIntake(AppUser as) throws Exception {
        return mvc.perform(get("/api/patients/{id}/intake", aminah.getId()).header("Authorization", bearer(as.getId())));
    }

    @Test
    void thereIsNoPreVisitReportUntilTheIntakeIsComplete() throws Exception {
        agentSays(false);
        chat().andExpect(status().isOk());

        readIntake(doctor).andExpect(status().isNotFound());
    }

    @Test
    void whenTheIntakeCompletesTheDoctorSeesThePreVisitReport() throws Exception {
        agentSays(true);
        when(agents.previsitReport(anyList())).thenReturn(REPORT);
        chat().andExpect(status().isOk()).andExpect(jsonPath("$.complete").value(true));

        String body = readIntake(doctor).andExpect(status().isOk())
                .andExpect(jsonPath("$.report.reason").value("Pening sejak 3 hari."))
                .andExpect(jsonPath("$.report.medicines[0].asWritten").value("Brand A 500mg"))
                .andExpect(jsonPath("$.report.medicines[0].generic").value("metformin"))
                .andExpect(jsonPath("$.report.askAbout[0]").value("jamu"))
                .andExpect(jsonPath("$.report.redFlags[0].level").value("watch"))
                .andExpect(jsonPath("$.transcript[4].content").value("Terima kasih."))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("590312-10-5566");
    }

    @Test
    void medicinesAndHerbsToldAtIntakeJoinTheMedicationListOnce() throws Exception {
        agentSays(true);
        when(agents.previsitReport(anyList())).thenReturn(REPORT);
        chat();
        chat();

        mvc.perform(get("/api/patients/{id}/medications", aminah.getId()).header("Authorization", bearer(doctor.getId())))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Brand A 500mg"))
                .andExpect(jsonPath("$[0].source").value("Told Khabar at intake"))
                .andExpect(jsonPath("$[1].name").value("peria"))
                .andExpect(jsonPath("$[1].kind").value("HERB"));
    }

    @Test
    void onlyThePatientAndTheirClinicCanReadTheIntake() throws Exception {
        agentSays(true);
        when(agents.previsitReport(anyList())).thenReturn(REPORT);
        chat();

        readIntake(aminahAccount).andExpect(status().isOk());
        readIntake(doctorElsewhere).andExpect(status().isForbidden());
        readIntake(nurul).andExpect(status().isForbidden());
    }

    @Test
    void theSafetyCheckIncludesAllergiesToldAtIntake() throws Exception {
        agentSays(true);
        when(agents.previsitReport(anyList())).thenReturn(REPORT);
        chat();
        when(agents.draftReport(anyString())).thenReturn(new DraftedReport("T2DM", "", "", null, List.of(), List.of()));
        when(agents.checkSafety(any())).thenReturn(new SafetyCheckResult(false, List.of()));

        String body = mvc.perform(post("/api/patients/{id}/encounters", aminah.getId()).header("Authorization", bearer(doctor.getId())))
                .andReturn().getResponse().getContentAsString();
        String encounterId = json.readTree(body).get("id").asText();
        mvc.perform(put("/api/encounters/{id}/notes", encounterId).header("Authorization", bearer(doctor.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Dx: T2DM\"}"));
        mvc.perform(post("/api/encounters/{id}/check", encounterId).header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk());

        verify(agents).checkSafety(argThat(draft -> draft.patient().allergies().contains("penicillin")));
    }

    @Test
    void ifTheReportCannotBeBuiltTheIntakeStillCompletes() throws Exception {
        agentSays(true);
        when(agents.previsitReport(anyList())).thenThrow(new IllegalStateException("agents service down"));

        chat().andExpect(status().isOk()).andExpect(jsonPath("$.complete").value(true));

        readIntake(doctor).andExpect(status().isOk())
                .andExpect(jsonPath("$.report").doesNotExist())
                .andExpect(jsonPath("$.transcript[1].role").value("user"));
    }
}
