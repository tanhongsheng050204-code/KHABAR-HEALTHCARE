package com.khabar.api.patients;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientRecordAccessTest {

    @Autowired MockMvc mvc;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @Autowired JdbcTemplate jdbc;

    AppUser doctorHere, doctorElsewhere, aminahAccount, nurul, formerCaregiver;
    Patient aminah, someoneElse;

    @BeforeEach
    void setUp() {
        Clinic here = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic elsewhere = clinics.save(new Clinic("Klinik Lain"));
        doctorHere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", here));
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", elsewhere));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        nurul = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Nurul", null));
        formerCaregiver = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Ex Carer", null));

        aminah = patients.save(new Patient(here, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        someoneElse = patients.save(new Patient(here, null, "Tan Kok Hoe", "540101-07-1234", "016-222 3333", "zh"));

        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));
        CaregiverLink revoked = new CaregiverLink(aminah, formerCaregiver, CaregiverScope.SUMMARY);
        revoked.revoke(Instant.now());
        caregiverLinks.save(revoked);
    }

    ResultActions view(Patient patient, AppUser as) throws Exception {
        return mvc.perform(get("/api/patients/{id}", patient.getId()).header("Authorization", bearer(as.getId())));
    }

    @Test
    void doctorAtThePatientsClinicSeesTheRecordWithTheIcMasked() throws Exception {
        view(aminah, doctorHere)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Aminah binti Yusof"))
                .andExpect(jsonPath("$.icMasked").value("******-**-5566"))
                .andExpect(content().string(not(containsString("590312"))));
    }

    @Test
    void doctorAtAnotherClinicIsRefused() throws Exception {
        view(aminah, doctorElsewhere).andExpect(status().isForbidden());
    }

    @Test
    void patientSeesTheirOwnRecord() throws Exception {
        view(aminah, aminahAccount).andExpect(status().isOk());
    }

    @Test
    void patientCannotSeeSomeoneElsesRecord() throws Exception {
        view(someoneElse, aminahAccount).andExpect(status().isForbidden());
    }

    @Test
    void caregiverWithConsentSeesTheRecord() throws Exception {
        view(aminah, nurul).andExpect(status().isOk());
    }

    @Test
    void caregiverWhoseConsentWasRevokedIsRefused() throws Exception {
        view(aminah, formerCaregiver).andExpect(status().isForbidden());
    }

    @Test
    void patientCanSeeWhoViewedTheirRecord() throws Exception {
        view(aminah, doctorHere).andExpect(status().isOk());
        view(aminah, nurul).andExpect(status().isOk());

        mvc.perform(get("/api/patients/{id}/access-log", aminah.getId()).header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("Nurul · caregiver"))
                .andExpect(jsonPath("$[0].action").value("VIEWED_RECORD"))
                .andExpect(jsonPath("$[1].actor").value("Dr Priya · Klinik Dr Priya"));
    }

    @Test
    void refusedAttemptsDoNotAppearAsViews() throws Exception {
        view(aminah, doctorElsewhere).andExpect(status().isForbidden());

        mvc.perform(get("/api/patients/{id}/access-log", aminah.getId()).header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherPatientCannotReadTheAccessLog() throws Exception {
        AppUser stranger = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Stranger", null));
        mvc.perform(get("/api/patients/{id}/access-log", aminah.getId()).header("Authorization", bearer(stranger.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void icAndPhoneAreEncryptedInTheDatabase() {
        String storedIc = jdbc.queryForObject("select ic_number_enc from patient where id = ?", String.class, aminah.getId());
        String storedPhone = jdbc.queryForObject("select phone_enc from patient where id = ?", String.class, aminah.getId());
        assertThat(storedIc).isNotBlank().doesNotContain("590312");
        assertThat(storedPhone).isNotBlank().doesNotContain("012-345 6789");
    }
}
