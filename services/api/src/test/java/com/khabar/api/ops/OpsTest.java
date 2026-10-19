package com.khabar.api.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.khabar.api.pathways.PatientPathwayRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The workload board and its balance button, the monthly outcomes report, and the patient import. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ClinicStaffAccess staffAccess;
    @Autowired PatientReplyRepository replies;
    @Autowired PatientPathwayRepository pathways;
    @MockBean AgentClientService agents;

    Clinic clinic;
    AppUser doctor, nurse, admin;

    @BeforeEach
    void setUp() {
        clock.reset();
        clinic = clinics.save(new Clinic("Klinik Operasi"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        nurse = users.save(new AppUser(UUID.randomUUID(), Role.NURSE, "Nurse Mei", clinic));
        staffAccess.grant(nurse, clinic, ClinicStaffRole.NURSE, doctor.getId(), clock.instant());
        admin = users.save(new AppUser(UUID.randomUUID(), Role.CLINIC_ADMIN, "Admin Lee", clinic));
        staffAccess.grant(admin, clinic, ClinicStaffRole.CLINIC_ADMIN, doctor.getId(), clock.instant());
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void balancingSpreadsUnownedCasesAcrossDoctorsAndNursesMostUrgentFirst() throws Exception {
        for (int i = 0; i < 4; i++) {
            Patient p = patients.save(new Patient(clinic, null, "Pesakit " + i, null, "012-600 00" + (10 + i), "ms"));
            replies.save(new PatientReply(p, "Pening", clock.instant().minus(Duration.ofMinutes(10 + i)), i == 0 ? TriageLevel.RED : TriageLevel.REVIEW, null));
        }
        // Opening the call list opens a case for each patient.
        mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId()))).andExpect(status().isOk());

        JsonNode before = body(mvc.perform(get("/api/clinic/workload").header("Authorization", bearer(nurse.getId()))).andExpect(status().isOk()));
        assertThat(before.get("unowned").asInt()).isEqualTo(4);
        assertThat(before.get("unownedUrgent").asInt()).isEqualTo(1);
        mvc.perform(post("/api/clinic/workload/balance").header("Authorization", bearer(nurse.getId()))).andExpect(status().isForbidden());

        JsonNode after = body(mvc.perform(post("/api/clinic/workload/balance").header("Authorization", bearer(admin.getId()))).andExpect(status().isOk()));
        assertThat(after.get("assigned").asInt()).isEqualTo(4);
        assertThat(after.get("board").get("unowned").asInt()).isZero();
        assertThat(after.get("board").get("staff")).hasSize(2).allMatch(p -> p.get("open").asInt() == 2);
    }

    @Test
    void theOutcomesReportCountsWithoutNamingAnyone() throws Exception {
        Patient p = patients.save(new Patient(clinic, null, "Siti Rahmah", null, "012-600 0099", "ms"));
        replies.save(new PatientReply(p, "Sakit dada", clock.instant().minus(Duration.ofHours(1)), TriageLevel.RED, "sakit dada"));
        JsonNode report = body(mvc.perform(get("/api/clinic/report").header("Authorization", bearer(admin.getId()))).andExpect(status().isOk()));
        assertThat(report.get("clinicName").asText()).isEqualTo("Klinik Operasi");
        assertThat(report.get("sections")).hasSizeGreaterThanOrEqualTo(5);
        assertThat(report.toString()).contains("\"key\":\"urgent\",\"label\"").doesNotContain("Siti");
        mvc.perform(get("/api/clinic/report").header("Authorization", bearer(nurse.getId()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/clinic/report?month=2099-01").header("Authorization", bearer(admin.getId()))).andExpect(status().isBadRequest());
    }

    @Test
    void anImportIsPreviewedThenSavesOnlyTheGoodRows() throws Exception {
        patients.save(new Patient(clinic, null, "Ada Sudah", "500101-01-1111", "012-700 0001", "ms"));
        String csv = "Name,IC,Phone,Language,Condition\r\n"
                + "Rahim bin Ali,590312-10-5566,012-700 0002,BM,diabetes\r\n"
                + "\"Tan, Ah Kow\",540101071234,012-700 0003,Chinese,hypertension\r\n"
                + "Bad IC,1234,012-700 0004,English,\r\n"
                + "Duplicate Phone,,012-700 0002,ms,\r\n"
                + "Already Here,500101-01-1111,012-700 0005,ta,\r\n"
                + "Odd Language,,012-700 0006,Klingon,\r\n";
        JsonNode preview = body(mvc.perform(post("/api/clinic/patients/import").header("Authorization", bearer(admin.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("csv", csv)))).andExpect(status().isOk()));
        assertThat(preview.get("saved").asBoolean()).isFalse();
        assertThat(preview.get("ok").asInt()).isEqualTo(2);
        assertThat(preview.get("problems").asInt()).isEqualTo(4);
        assertThat(preview.get("rows").get(1).get("fullName").asText()).isEqualTo("Tan, Ah Kow");
        assertThat(preview.get("rows").get(1).get("language").asText()).isEqualTo("zh");
        assertThat(preview.get("rows").get(3).get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(patients.findByClinicId(clinic.getId())).hasSize(1);

        mvc.perform(post("/api/clinic/patients/import?dryRun=false").header("Authorization", bearer(admin.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("csv", csv))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(patients.findByClinicId(clinic.getId())).hasSize(3);
        Patient rahim = patients.findByClinicId(clinic.getId()).stream().filter(x -> x.getFullName().startsWith("Rahim")).findFirst().orElseThrow();
        assertThat(pathways.findByPatientIdAndEndedAtIsNullOrderByStartedAt(rahim.getId())).extracting(pp -> pp.getPathway()).containsExactly("DIABETES");
        mvc.perform(post("/api/clinic/patients/import").header("Authorization", bearer(nurse.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("csv", csv)))).andExpect(status().isForbidden());
    }
}
