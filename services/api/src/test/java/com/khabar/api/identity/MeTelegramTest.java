package com.khabar.api.identity;

import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.TelegramChatIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeTelegramTest {

    @Autowired MockMvc mvc;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired TelegramChatIndex chatIndex;

    Patient patientWithAccount(String name) {
        Clinic clinic = clinics.save(new Clinic("Klinik Me"));
        AppUser account = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, name, null));
        return patients.save(new Patient(clinic, account, name, "590312-10-5566", "012-000 0001", "ms"));
    }

    @Test
    void aPatientWhoLinkedTelegramSeesItAsLinked() throws Exception {
        Patient p = patientWithAccount("Aminah");
        p.linkTelegram("123", chatIndex.of("123"));
        patients.save(p);
        mvc.perform(get("/api/me").header("Authorization", bearer(p.getAccount().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramLinked").value(true));
    }

    @Test
    void aPatientWhoHasNotLinkedSeesItAsNotLinked() throws Exception {
        Patient p = patientWithAccount("Rosnah");
        mvc.perform(get("/api/me").header("Authorization", bearer(p.getAccount().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramLinked").value(false));
    }
}
