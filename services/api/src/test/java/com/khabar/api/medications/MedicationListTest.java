package com.khabar.api.medications;

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
import com.khabar.api.service.AgentDtos.CurrentMed;
import com.khabar.api.service.AgentDtos.DraftedReport;
import com.khabar.api.service.AgentDtos.SafetyCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MedicationListTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @MockBean AgentClientService agents;

    AppUser doctor, doctorElsewhere, aminahAccount, nurul;
    Patient aminah;

    @BeforeEach
    void setUp() {
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        nurul = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Nurul", null));
        aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY));
    }

    ResultActions add(AppUser as, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/patients/{id}/medications", aminah.getId()).header("Authorization", bearer(as.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    ResultActions list(AppUser as) throws Exception {
        return mvc.perform(get("/api/patients/{id}/medications", aminah.getId()).header("Authorization", bearer(as.getId())));
    }

    @Test
    void thePatientListsWhatTheyTakeFromOtherPlaces() throws Exception {
        add(aminahAccount, Map.of("name", "Brand A 500mg", "kind", "MEDICINE", "source", "Klinik Kesihatan"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Brand A 500mg"))
                .andExpect(jsonPath("$.addedBy").value("PATIENT"));
        add(aminahAccount, Map.of("name", "Teh hijau", "kind", "HERB", "source", "a relative")).andExpect(status().isCreated());

        list(aminahAccount).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Brand A 500mg"))
                .andExpect(jsonPath("$[0].source").value("Klinik Kesihatan"))
                .andExpect(jsonPath("$[1].kind").value("HERB"));
    }

    @Test
    void aCaregiverCanAddToTheListAndTheDoctorSeesWhoAddedIt() throws Exception {
        add(nurul, Map.of("name", "Amlodipine 5mg", "source", "pharmacy")).andExpect(status().isCreated());

        list(doctor).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Amlodipine 5mg"))
                .andExpect(jsonPath("$[0].kind").value("MEDICINE"))
                .andExpect(jsonPath("$[0].addedBy").value("CAREGIVER"));
    }

    @Test
    void aDoctorAtAnotherClinicCannotSeeOrChangeTheList() throws Exception {
        list(doctorElsewhere).andExpect(status().isForbidden());
        add(doctorElsewhere, Map.of("name", "Aspirin")).andExpect(status().isForbidden());
    }

    @Test
    void aMedicineNeedsAName() throws Exception {
        add(aminahAccount, Map.of("name", "  ")).andExpect(status().isBadRequest());
    }

    @Test
    void stoppingAMedicineTakesItOffTheList() throws Exception {
        String body = add(aminahAccount, Map.of("name", "Brand A 500mg")).andReturn().getResponse().getContentAsString();
        String itemId = json.readTree(body).get("id").asText();

        mvc.perform(delete("/api/patients/{id}/medications/{item}", aminah.getId(), itemId).header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk());

        list(aminahAccount).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theSafetyCheckSeesEverythingOnTheList() throws Exception {
        add(aminahAccount, Map.of("name", "Brand A 500mg", "source", "Klinik Kesihatan"));
        add(aminahAccount, Map.of("name", "Teh hijau", "kind", "HERB"));
        when(agents.draftReport(anyString())).thenReturn(new DraftedReport("T2DM", "", "", null, List.of(), List.of()));
        when(agents.checkSafety(any())).thenReturn(new SafetyCheckResult(false, List.of()));

        String body = mvc.perform(post("/api/patients/{id}/encounters", aminah.getId()).header("Authorization", bearer(doctor.getId())))
                .andReturn().getResponse().getContentAsString();
        String encounterId = json.readTree(body).get("id").asText();
        mvc.perform(put("/api/encounters/{id}/notes", encounterId).header("Authorization", bearer(doctor.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Dx: T2DM\"}"));
        mvc.perform(post("/api/encounters/{id}/check", encounterId).header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk());

        verify(agents).checkSafety(argThat(draft ->
                draft.currentMeds().equals(List.of(new CurrentMed("Brand A 500mg", "Klinik Kesihatan")))
                        && draft.herbs().equals(List.of("Teh hijau"))));
    }

    @Test
    void aConsentedPacketPhotoIsForwardedEphemerallyForAnAllowedUser() throws Exception {
        when(agents.readMedicinePacket(any(byte[].class), anyString())).thenReturn(Map.of(
                "candidates", List.of(Map.of("brand", "Norvasc", "generic_candidate", "amlodipine")),
                "unreadable", false, "message", ""));
        MockMultipartFile image = new MockMultipartFile("image", "packet.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});

        mvc.perform(multipart("/api/patients/{id}/medications/packet-photo", aminah.getId())
                        .file(image).param("consentConfirmed", "true")
                        .header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].brand").value("Norvasc"))
                .andExpect(jsonPath("$.candidates[0].generic_candidate").value("amlodipine"));
        verify(agents).readMedicinePacket(any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void aPacketPhotoCannotBeSentWithoutConsentOrFromAnotherClinic() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "packet.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});

        mvc.perform(multipart("/api/patients/{id}/medications/packet-photo", aminah.getId())
                        .file(image).param("consentConfirmed", "false")
                        .header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/patients/{id}/medications/packet-photo", aminah.getId())
                        .file(image).param("consentConfirmed", "true")
                        .header("Authorization", bearer(doctorElsewhere.getId())))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(agents);
    }

    @Test
    void aPacketPhotoMustHaveAnAllowedImageSignature() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("image", "packet.jpg", "image/jpeg", "not an image".getBytes());
        mvc.perform(multipart("/api/patients/{id}/medications/packet-photo", aminah.getId())
                        .file(fake).param("consentConfirmed", "true")
                        .header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(agents);
    }
}
