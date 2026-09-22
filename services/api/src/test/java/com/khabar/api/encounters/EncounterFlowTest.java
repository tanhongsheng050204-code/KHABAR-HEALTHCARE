package com.khabar.api.encounters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CheckInRepository;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos.DraftedReport;
import com.khabar.api.service.AgentDtos.DraftedRx;
import com.khabar.api.service.AgentDtos.FindingDto;
import com.khabar.api.service.AgentDtos.SafetyCheckResult;
import com.khabar.api.service.AgentDtos.SummaryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
class EncounterFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CheckInRepository checkIns;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdjustableClock clock;
    @MockBean AgentClientService agents;

    AppUser doctor, doctorElsewhere, aminahAccount;
    Patient aminah;

    static final String NOTES = "Aminah binti Yusof 590312-10-5566 c/o giddiness\nDx: T2DM\nT. Metformin 500mg 1/1 BD PC\nTCA 2/52";
    static final DraftedRx METFORMIN = new DraftedRx("T. Metformin 500mg 1/1 BD PC", "Metformin", 500.0, 1.0, 2, List.of("morning", "night"), "after_food", false);
    static final DraftedReport DRAFT = new DraftedReport("T2DM", "", "TCA 2/52", 2.0, List.of(), List.of(METFORMIN));
    static final FindingDto CRITICAL = new FindingDto("duplicate", "CRITICAL", "Metformin is already taken as 'Brand A' from Klinik A.");

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));

        when(agents.draftReport(anyString())).thenReturn(DRAFT);
        when(agents.checkSafety(any())).thenReturn(new SafetyCheckResult(false, List.of()));
        when(agents.buildSummary(anyList(), anyString(), any(), anyBoolean())).thenReturn(new SummaryResult("ms",
                List.of(new com.khabar.api.service.AgentDtos.MedicineLine("Metformin 500 mg", "1 biji, pagi dan malam, selepas makan.")),
                "Kalau rasa lebih teruk, datang ke klinik segera.", "Datang semula dalam 2 minggu.", List.of(),
                "• Metformin 500 mg: 1 biji, pagi dan malam, selepas makan."));
    }

    String startVisit(AppUser as) throws Exception {
        String body = mvc.perform(post("/api/patients/{id}/encounters", aminah.getId()).header("Authorization", bearer(as.getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    ResultActions writeNotes(String encounterId, String notes) throws Exception {
        return mvc.perform(put("/api/encounters/{id}/notes", encounterId).header("Authorization", bearer(doctor.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("notes", notes))));
    }

    ResultActions check(String encounterId) throws Exception {
        return mvc.perform(post("/api/encounters/{id}/check", encounterId).header("Authorization", bearer(doctor.getId())));
    }

    ResultActions finalise(String encounterId) throws Exception {
        return mvc.perform(post("/api/encounters/{id}/finalise", encounterId).header("Authorization", bearer(doctor.getId())));
    }

    @Test
    void theDoctorStartsAVisitAndDraftsTheReportFromNotes() throws Exception {
        String id = startVisit(doctor);
        writeNotes(id, NOTES).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.diagnosis").value("T2DM"))
                .andExpect(jsonPath("$.prescription[0].name").value("Metformin"))
                .andExpect(jsonPath("$.prescription[0].timesPerDay").value(2));
    }

    @Test
    void notesSentToTheReportAgentHaveThePatientsIdentityRemoved() throws Exception {
        writeNotes(startVisit(doctor), NOTES);
        verify(agents).draftReport(argThat(n -> !n.contains("Aminah") && !n.contains("590312-10-5566") && n.contains("Metformin")));
    }

    @Test
    void finalisingIsRefusedUntilTheSafetyCheckHasRun() throws Exception {
        String id = startVisit(doctor);
        writeNotes(id, NOTES);
        finalise(id).andExpect(status().isConflict());
    }

    @Test
    void aCriticalFindingBlocksFinalising() throws Exception {
        when(agents.checkSafety(any())).thenReturn(new SafetyCheckResult(true, List.of(CRITICAL)));
        String id = startVisit(doctor);
        writeNotes(id, NOTES);
        check(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.openCriticalFindings").value(1));
        finalise(id).andExpect(status().isConflict());
    }

    @Test
    void aWrittenReasonUnblocksFinalisingAndIsAudited() throws Exception {
        when(agents.checkSafety(any())).thenReturn(new SafetyCheckResult(true, List.of(CRITICAL)));
        String id = startVisit(doctor);
        writeNotes(id, NOTES);
        String body = check(id).andReturn().getResponse().getContentAsString();
        String findingId = json.readTree(body).get("findings").get(0).get("id").asText();

        mvc.perform(post("/api/encounters/{id}/findings/{fid}/override", id, findingId).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Stopping Brand A today; told patient to take only this one\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCriticalFindings").value(0));
        finalise(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINAL"));

        mvc.perform(get("/api/patients/{id}/access-log", aminah.getId()).header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(jsonPath("$[?(@.action == 'OVERRODE_SAFETY_FINDING')]").exists());
    }

    @Test
    void anOverrideNeedsARealReason() throws Exception {
        when(agents.checkSafety(any())).thenReturn(new SafetyCheckResult(true, List.of(CRITICAL)));
        String id = startVisit(doctor);
        writeNotes(id, NOTES);
        String findingId = json.readTree(check(id).andReturn().getResponse().getContentAsString()).get("findings").get(0).get("id").asText();
        mvc.perform(post("/api/encounters/{id}/findings/{fid}/override", id, findingId).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"ok\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theDatabaseItselfRefusesAFinalReportWithAnOpenCriticalFinding() throws Exception {
        when(agents.checkSafety(any())).thenReturn(new SafetyCheckResult(true, List.of(CRITICAL)));
        String id = startVisit(doctor);
        writeNotes(id, NOTES);
        check(id);
        assertThatThrownBy(() -> jdbc.update("update encounter set status = 'FINAL' where id = ?", UUID.fromString(id)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void changingTheNotesAfterTheCheckMeansCheckingAgain() throws Exception {
        String id = startVisit(doctor);
        writeNotes(id, NOTES);
        check(id).andExpect(status().isOk());
        writeNotes(id, NOTES + "\nT. Amlodipine 5mg OD");
        finalise(id).andExpect(status().isConflict());
    }

    @Test
    void finalisingStartsTheFollowUpWithFiveCheckInsAndThePatientsSummary() throws Exception {
        String id = startVisit(doctor);
        writeNotes(id, NOTES);
        check(id);
        finalise(id).andExpect(status().isOk());

        LocalDate today = LocalDate.now(clock);
        assertThat(patients.findById(aminah.getId()).orElseThrow().getFollowUpStart()).isEqualTo(today);
        assertThat(checkIns.findByPatientIdOrderByDueDate(aminah.getId())).extracting(c -> c.getDayNumber())
                .containsExactly(1, 3, 7, 14, 30);

        mvc.perform(get("/api/patients/{id}/summary", aminah.getId()).header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("ms"))
                .andExpect(jsonPath("$.text").value("• Metformin 500 mg: 1 biji, pagi dan malam, selepas makan."));
    }

    @Test
    void finalisingStillWorksWhenTheSummaryServiceIsDown() throws Exception {
        when(agents.buildSummary(anyList(), anyString(), any(), anyBoolean())).thenThrow(new IllegalStateException("agents down"));
        String id = startVisit(doctor);
        writeNotes(id, NOTES);
        check(id);
        finalise(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINAL"));
        mvc.perform(get("/api/patients/{id}/summary", aminah.getId()).header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDoctorAtAnotherClinicCannotStartOrOpenTheVisit() throws Exception {
        mvc.perform(post("/api/patients/{id}/encounters", aminah.getId()).header("Authorization", bearer(doctorElsewhere.getId())))
                .andExpect(status().isForbidden());
        String id = startVisit(doctor);
        mvc.perform(get("/api/encounters/{id}", id).header("Authorization", bearer(doctorElsewhere.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientsCannotStartVisits() throws Exception {
        mvc.perform(post("/api/patients/{id}/encounters", aminah.getId()).header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isForbidden());
    }
}
