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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClinicPatientListTest {

    @Autowired MockMvc mvc;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;

    AppUser doctor, aminahAccount;

    @BeforeEach
    void setUp() {
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        Patient aminah = new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms");
        aminah.startFollowUp(LocalDate.now().minusDays(3));
        patients.save(aminah);
        patients.save(new Patient(clinic, null, "Tan Kok Hoe", "540101-07-1234", "016-222 3333", "zh"));
        patients.save(new Patient(other, null, "Someone Elsewhere", "600101-01-1111", "017-000 0000", "en"));
    }

    @Test
    void aDoctorSeesTheirOwnClinicsPatientsByNameWithTheIcMasked() throws Exception {
        mvc.perform(get("/api/clinic/patients").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fullName").value("Aminah binti Yusof"))
                .andExpect(jsonPath("$[0].followUpDay").value(3))
                .andExpect(jsonPath("$[0].hasAccount").value(true))
                .andExpect(jsonPath("$[1].fullName").value("Tan Kok Hoe"))
                .andExpect(jsonPath("$[1].followUpDay").doesNotExist())
                .andExpect(content().string(not(containsString("590312-10-5566"))))
                .andExpect(content().string(not(containsString("012-345 6789"))));
    }

    @Test
    void patientsCannotListTheClinic() throws Exception {
        mvc.perform(get("/api/clinic/patients").header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isForbidden());
    }
}
