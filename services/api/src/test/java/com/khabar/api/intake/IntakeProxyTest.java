package com.khabar.api.intake;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntakeProxyTest {

    @Autowired MockMvc mvc;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @MockBean AgentClientService agents;

    AppUser aminahAccount, doctor;
    Patient aminah;

    static final String BODY = """
            {"messages": [{"role": "user", "content": "Saya Aminah. IC 590312-10-5566, telefon 012-345 6789. Saya pening."}]}
            """;

    @BeforeEach
    void setUp() {
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        when(agents.processIntake(anyString(), anyString(), anyList()))
                .thenReturn(Map.of("next_question", "Sejak bila rasa pening?", "is_complete", false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void forwardsThePatientsGraphIdAndLanguageButNeverTheirIdentity() throws Exception {
        mvc.perform(post("/api/intake/chat").header("Authorization", bearer(aminahAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextQuestion").value("Sejak bila rasa pening?"))
                .andExpect(jsonPath("$.complete").value(false));

        ArgumentCaptor<List<Map<String, String>>> sent = ArgumentCaptor.forClass(List.class);
        verify(agents).processIntake(org.mockito.ArgumentMatchers.eq(aminah.getGraphId().toString()), org.mockito.ArgumentMatchers.eq("ms"), sent.capture());
        String forwarded = sent.getValue().toString();
        assertThat(forwarded).doesNotContain("Aminah", "590312-10-5566", "012-345 6789").contains("pening");
    }

    @Test
    void requiresSignIn() throws Exception {
        mvc.perform(post("/api/intake/chat").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(agents, never()).processIntake(anyString(), anyString(), any());
    }

    @Test
    void isOnlyForPatients() throws Exception {
        mvc.perform(post("/api/intake/chat").header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }
}
