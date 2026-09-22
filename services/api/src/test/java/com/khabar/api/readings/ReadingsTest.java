package com.khabar.api.readings;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F4: blood pressure and blood sugar from a home device (Favoriot) or typed in; worrying numbers reach the call list. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReadingsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CaregiverLinkRepository caregiverLinks;

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
        aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "03-0000 0001", "ms"));
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));
    }

    ResultActions record(AppUser as, Map<String, Object> reading) throws Exception {
        return mvc.perform(post("/api/patients/{id}/readings", aminah.getId()).header("Authorization", bearer(as.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(reading)));
    }

    ResultActions callList() throws Exception {
        return mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())));
    }

    ResultActions favoriot(String secret, String body) throws Exception {
        return mvc.perform(post("/api/webhooks/favoriot").header("X-Khabar-Device-Secret", secret)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void aVeryLowSugarReadingPutsThePatientAtTheTopOfTheCallList() throws Exception {
        record(aminahAccount, Map.of("glucose", 2.8)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("RED"))
                .andExpect(jsonPath("$.description").value("Blood sugar 2.8 mmol/L (very low)"));

        callList().andExpect(jsonPath("$.items[0].fullName").value("Aminah binti Yusof"))
                .andExpect(jsonPath("$.items[0].level").value("RED"))
                .andExpect(jsonPath("$.items[0].reason").value("READING"))
                .andExpect(jsonPath("$.items[0].urgentReply").value("Blood sugar 2.8 mmol/L (very low)"));
    }

    @Test
    void anOrdinaryReadingIsKeptButNeedsNoCall() throws Exception {
        record(aminahAccount, Map.of("systolic", 128, "diastolic", 82)).andExpect(jsonPath("$.level").value("OK"));

        callList().andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/patients/{id}/readings", aminah.getId()).header("Authorization", bearer(doctor.getId())))
                .andExpect(jsonPath("$[0].description").value("Blood pressure 128/82"));
    }

    @Test
    void aCaregiverCanRecordButOtherClinicsCannotAndNonsenseIsRefused() throws Exception {
        record(nurul, Map.of("systolic", 165, "diastolic", 95)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("caregiver"));
        record(doctorElsewhere, Map.of("glucose", 5.0)).andExpect(status().isForbidden());
        record(aminahAccount, Map.of("glucose", 90.0)).andExpect(status().isBadRequest());
        record(aminahAccount, Map.of("systolic", 120)).andExpect(status().isBadRequest());
    }

    @Test
    void callingThePatientClearsTheReading() throws Exception {
        record(aminahAccount, Map.of("glucose", 2.8));
        mvc.perform(post("/api/clinic/call-list/{id}/called", aminah.getId()).header("Authorization", bearer(doctor.getId())));
        callList().andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void aLinkedFavoriotDeviceSendsReadingsStraightIn() throws Exception {
        mvc.perform(put("/api/patients/{id}/device", aminah.getId()).header("Authorization", bearer(doctor.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\": \"glucometer-aminah@demo\"}"))
                .andExpect(status().isOk());

        favoriot("test-device-secret", "{\"device_developer_id\": \"glucometer-aminah@demo\", \"data\": {\"glucose\": \"2.9\"}}")
                .andExpect(status().isOk());

        callList().andExpect(jsonPath("$.items[0].reason").value("READING"))
                .andExpect(jsonPath("$.items[0].urgentReply").value("Blood sugar 2.9 mmol/L (very low)"));
        mvc.perform(get("/api/patients/{id}/readings", aminah.getId()).header("Authorization", bearer(doctor.getId())))
                .andExpect(jsonPath("$[0].source").value("favoriot"));
    }

    @Test
    void theDeviceWebhookNeedsTheSecretAndIgnoresUnknownDevices() throws Exception {
        favoriot("wrong", "{\"device_developer_id\": \"x\", \"data\": {\"glucose\": 2.9}}").andExpect(status().isForbidden());
        favoriot("test-device-secret", "{\"device_developer_id\": \"nobody\", \"data\": {\"glucose\": 2.9}}").andExpect(status().isOk());
        callList().andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void onlyTheClinicsDoctorLinksADevice() throws Exception {
        mvc.perform(put("/api/patients/{id}/device", aminah.getId()).header("Authorization", bearer(aminahAccount.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\": \"mine\"}"))
                .andExpect(status().isForbidden());
    }
}
