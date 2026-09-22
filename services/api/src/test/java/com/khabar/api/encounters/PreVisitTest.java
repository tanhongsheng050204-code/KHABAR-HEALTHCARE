package com.khabar.api.encounters;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.PatientReply;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.medications.MedicationItem;
import com.khabar.api.medications.MedicationItemRepository;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos.CurrentMed;
import com.khabar.api.service.AgentDtos.DraftedReport;
import com.khabar.api.service.AgentDtos.DraftedRx;
import com.khabar.api.service.AgentDtos.FindingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What the doctor reads before calling the patient in: intake, last visit, what they take, what they said since. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreVisitTest {

    @Autowired MockMvc mvc;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired EncounterRepository encounters;
    @Autowired MedicationItemRepository medications;
    @Autowired PatientReplyRepository replies;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @MockBean AgentClientService agents;

    AppUser doctor, doctorElsewhere, aminahAccount, nurul;
    Patient aminah;

    static final FindingDto DUPLICATE = new FindingDto("duplicate", "CRITICAL", "Metformin is taken 2 times.");

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        nurul = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Nurul", null));
        Patient p = new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "03-0000 0001", "ms");
        p.recordAllergies(List.of("penicillin"));
        aminah = patients.save(p);
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));
    }

    ResultActions previsit(AppUser as) throws Exception {
        return mvc.perform(get("/api/patients/{id}/previsit", aminah.getId()).header("Authorization", bearer(as.getId())));
    }

    void lastVisitSixWeeksAgo() {
        Encounter visit = new Encounter(aminah, doctor, clock.instant().minus(Duration.ofDays(42)));
        visit.applyDraft("Dx: T2DM\nT. Metformin 500mg 1/1 BD PC\nTCA 6/52", new DraftedReport("T2DM", "Diet advice", "TCA 6/52", 6.0, List.of(),
                List.of(new DraftedRx("T. Metformin 500mg 1/1 BD PC", "Metformin", 500.0, 1.0, 2, List.of("morning", "night"), "after_food", false))), false);
        visit.recordFindings(List.of());
        visit.finalise(clock.instant().minus(Duration.ofDays(42)));
        encounters.save(visit);
    }

    @Test
    void theDoctorSeesTheLastVisitWhatSheTakesAndWhatSheSaidSince() throws Exception {
        lastVisitSixWeeksAgo();
        medications.save(new MedicationItem(aminah, "Metformin 500mg", MedicationItem.Kind.MEDICINE, "Klinik Kesihatan", Role.PATIENT, aminahAccount.getId(), clock.instant()));
        medications.save(new MedicationItem(aminah, "Jus peria", MedicationItem.Kind.HERB, "Her sister", Role.PATIENT, aminahAccount.getId(), clock.instant().plusSeconds(1)));
        PatientReply reply = new PatientReply(aminah, "Lupa makan ubat semalam", clock.instant().minus(Duration.ofDays(3)), TriageLevel.REVIEW, null);
        reply.markMissedDose();
        replies.save(reply);
        when(agents.reconcile(any(), anyList(), anyList())).thenReturn(List.of(DUPLICATE));

        previsit(doctor).andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.fullName").value("Aminah binti Yusof"))
                .andExpect(jsonPath("$.patient.allergies[0]").value("penicillin"))
                .andExpect(jsonPath("$.lastVisit.diagnosis").value("T2DM"))
                .andExpect(jsonPath("$.lastVisit.prescription[0]").value("T. Metformin 500mg 1/1 BD PC"))
                .andExpect(jsonPath("$.medications.length()").value(2))
                .andExpect(jsonPath("$.reconciliation[0].check").value("duplicate"))
                .andExpect(jsonPath("$.recentReplies[0].text").value("Lupa makan ubat semalam"))
                .andExpect(jsonPath("$.recentReplies[0].missedDose").value(true))
                .andExpect(jsonPath("$.intake").doesNotExist());

        verify(agents).reconcile(argThat(facts -> facts.allergies().contains("penicillin")),
                eq(List.of(new CurrentMed("Metformin 500mg", "Klinik Kesihatan"))), eq(List.of("Jus peria")));
    }

    @Test
    void aNewPatientHasAnEmptyButValidPage() throws Exception {
        previsit(doctor).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastVisit").doesNotExist())
                .andExpect(jsonPath("$.medications.length()").value(0))
                .andExpect(jsonPath("$.recentReplies.length()").value(0));
    }

    @Test
    void ifTheAgentsAreDownTheRestOfThePageStillLoads() throws Exception {
        medications.save(new MedicationItem(aminah, "Metformin 500mg", MedicationItem.Kind.MEDICINE, "Klinik Kesihatan", Role.PATIENT, aminahAccount.getId(), clock.instant()));
        when(agents.reconcile(any(), anyList(), anyList())).thenThrow(new IllegalStateException("agents down"));

        previsit(doctor).andExpect(status().isOk())
                .andExpect(jsonPath("$.medications.length()").value(1))
                .andExpect(jsonPath("$.reconciliation").doesNotExist());
    }

    @Test
    void itIsForTheClinicsDoctorsAndTheViewIsLogged() throws Exception {
        previsit(doctorElsewhere).andExpect(status().isForbidden());
        previsit(nurul).andExpect(status().isForbidden());
        previsit(aminahAccount).andExpect(status().isForbidden());

        previsit(doctor).andExpect(status().isOk());
        mvc.perform(get("/api/patients/{id}/access-log", aminah.getId()).header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(jsonPath("$[0].action").value("VIEWED_PREVISIT"));
    }
}
