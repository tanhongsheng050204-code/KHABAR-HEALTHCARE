package com.khabar.api.encounters;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V2: the doctor speaks instead of typing; the text comes back for them to check before it becomes notes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TranscriptionTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @MockBean AgentClientService agents;

    AppUser doctor, doctorElsewhere;
    String encounterId;

    static final MockMultipartFile AUDIO = new MockMultipartFile("audio", "visit.webm", "audio/webm", new byte[]{1, 2, 3, 4});

    @BeforeEach
    void setUp() throws Exception {
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        Patient aminah = patients.save(new Patient(clinic, null, "Aminah binti Yusof", "590312-10-5566", "03-0000 0001", "ms"));
        String body = mvc.perform(post("/api/patients/{id}/encounters", aminah.getId()).header("Authorization", bearer(doctor.getId())))
                .andReturn().getResponse().getContentAsString();
        encounterId = json.readTree(body).get("id").asText();
    }

    ResultActions upload(AppUser as, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/encounters/{id}/audio", encounterId).file(file).header("Authorization", bearer(as.getId())));
    }

    @Test
    void theDoctorGetsTheTextBackToCheck() throws Exception {
        when(agents.transcribe(any(), anyString(), anyString())).thenReturn("c/o pening 3/7. Dx T2DM. T Metformin 500 mg BD.");

        upload(doctor, AUDIO).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("c/o pening 3/7. Dx T2DM. T Metformin 500 mg BD."));
        verify(agents).transcribe(eq(AUDIO.getBytes()), eq("visit.webm"), eq("ms"));
    }

    @Test
    void onlyTheClinicsDoctorsCanUseIt() throws Exception {
        upload(doctorElsewhere, AUDIO).andExpect(status().isForbidden());
        verify(agents, never()).transcribe(any(), anyString(), anyString());
    }

    @Test
    void anEmptyRecordingIsRefused() throws Exception {
        upload(doctor, new MockMultipartFile("audio", "empty.webm", "audio/webm", new byte[0]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenTranscriptionIsNotAvailableTheDoctorIsToldToType() throws Exception {
        when(agents.transcribe(any(), anyString(), anyString())).thenThrow(new IllegalStateException("no key"));

        upload(doctor, AUDIO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Speech to text is not available right now. Type the notes instead."));
    }
}
