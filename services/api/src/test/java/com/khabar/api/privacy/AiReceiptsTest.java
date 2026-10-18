package com.khabar.api.privacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "What the AI saw": a receipt for each time patient text leaves for the AI service, visible to the patient and clinic. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiReceiptsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @Autowired ClinicStaffAccess staffAccess;
    @Autowired AiReceiptRepository receipts;
    @MockBean AgentClientService agents;

    AppUser doctor, otherDoctor, aminahAccount, caregiver;
    Patient aminah;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Resit"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        otherDoctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        staffAccess.grant(otherDoctor, other, ClinicStaffRole.DOCTOR, otherDoctor.getId(), clock.instant());
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        caregiver = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Nurul", null));
        Patient p = new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms");
        p.startFollowUp(LocalDate.now(clock).minusDays(3));
        aminah = patients.save(p);
        caregiverLinks.save(new CaregiverLink(aminah, caregiver, CaregiverScope.SUMMARY_AND_ALERTS));
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "review", "matched", "pening"));
    }

    @Test
    void aReplyLeavesAReceiptWithItsIdentifiersRemovedAndCounted() throws Exception {
        mvc.perform(post("/api/followup/replies").header("Authorization", bearer(aminahAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("text", "Saya Aminah, IC 590312-10-5566, pening sikit. Call 012-345 6789"))))
                .andExpect(status().isOk());

        var stored = receipts.findByPatientIdOrderByIdAsc(aminah.getId());
        assertThat(stored).hasSize(1);
        AiReceipt receipt = stored.get(0);
        assertThat(receipt.getPurpose()).isEqualTo("REPLY_TRIAGE");
        assertThat(receipt.getSent()).doesNotContain("Aminah").doesNotContain("590312").doesNotContain("6789")
                .contains("[NAME]").contains("[IC]").contains("[PHONE]");
        assertThat(receipt.getNamesRemoved()).isEqualTo(1);
        assertThat(receipt.getIcsRemoved()).isEqualTo(1);
        assertThat(receipt.getPhonesRemoved()).isEqualTo(1);

        mvc.perform(get("/api/patients/" + aminah.getId() + "/ai-receipts").header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].purposeLabel").value("Reading a check-in reply"))
                .andExpect(jsonPath("$[0].namesRemoved").value(1));
        mvc.perform(get("/api/patients/" + aminah.getId() + "/ai-receipts").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endpoint").value("/agents/followup/triage"));
    }

    @Test
    void caregiversAndOtherClinicsCannotReadReceipts() throws Exception {
        mvc.perform(get("/api/patients/" + aminah.getId() + "/ai-receipts").header("Authorization", bearer(caregiver.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patients/" + aminah.getId() + "/ai-receipts").header("Authorization", bearer(otherDoctor.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void countsEachIdentifierToken() {
        assertThat(AiReceipts.count("[NAME] and [NAME], [IC]", "[NAME]")).isEqualTo(2);
        assertThat(AiReceipts.count("nothing here", "[PHONE]")).isZero();
    }
}
