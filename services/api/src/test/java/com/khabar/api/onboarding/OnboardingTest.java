package com.khabar.api.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** How real (Supabase) users become a clinic's doctors, patients and caregivers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;

    AppUser doctor;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
    }

    JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    ResultActions postJson(String path, UUID as, String content) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer(as)).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    JsonNode registerAminah() throws Exception {
        return body(postJson("/api/clinic/patients", doctor.getId(), """
                {"fullName":"Aminah binti Yusof","icNumber":"590312-10-5566","phone":"012-345 6789","preferredLanguage":"ms",
                 "allergies":["penicillin"],"pregnant":false}""").andExpect(status().isCreated()));
    }

    ResultActions accept(String code, UUID newUser, String displayName) throws Exception {
        return postJson("/api/invites/" + code + "/accept", newUser, "{\"displayName\":\"" + displayName + "\"}");
    }

    @Test
    void aDoctorRegistersAPatientWhoThenLinksTheirOwnSignIn() throws Exception {
        JsonNode registered = registerAminah();
        String code = registered.get("inviteCode").asText();
        UUID supabaseUser = UUID.randomUUID();

        accept(code, supabaseUser, "Aminah").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PATIENT"));
        mvc.perform(get("/api/me").header("Authorization", bearer(supabaseUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(registered.get("patientId").asText()));
    }

    @Test
    void patientRegistrationRejectsMalformedIcNumbers() throws Exception {
        postJson("/api/clinic/patients", doctor.getId(), """
                {"fullName":"Fictional Test Patient","icNumber":"invalid","preferredLanguage":"en"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void anInviteCodeWorksOnlyOnce() throws Exception {
        String code = registerAminah().get("inviteCode").asText();
        accept(code, UUID.randomUUID(), "Aminah").andExpect(status().isOk());
        accept(code, UUID.randomUUID(), "Someone else").andExpect(status().isGone());
    }

    @Test
    void anInviteExpiresAfterSevenDays() throws Exception {
        String code = registerAminah().get("inviteCode").asText();
        clock.advance(Duration.ofDays(8));
        accept(code, UUID.randomUUID(), "Aminah").andExpect(status().isGone());
    }

    @Test
    void aWrongCodeIsNotFound() throws Exception {
        accept("NOTACODE12", UUID.randomUUID(), "x").andExpect(status().isNotFound());
    }

    @Test
    void onlyDoctorsRegisterPatients() throws Exception {
        postJson("/api/clinic/patients", UUID.randomUUID(), "{\"fullName\":\"X\",\"preferredLanguage\":\"en\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void aPatientInvitesACaregiverAndCanRevokeThem() throws Exception {
        JsonNode registered = registerAminah();
        UUID aminah = UUID.randomUUID();
        accept(registered.get("inviteCode").asText(), aminah, "Aminah");
        String patientId = registered.get("patientId").asText();

        String caregiverCode = body(postJson("/api/patients/me/caregiver-invites", aminah, "{\"scope\":\"SUMMARY_AND_ALERTS\"}")
                .andExpect(status().isCreated())).get("inviteCode").asText();
        UUID nurul = UUID.randomUUID();
        accept(caregiverCode, nurul, "Nurul").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CAREGIVER"));

        mvc.perform(get("/api/me").header("Authorization", bearer(nurul)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientIds[0]").value(patientId));
        mvc.perform(get("/api/patients/{id}", patientId).header("Authorization", bearer(nurul))).andExpect(status().isOk());

        JsonNode caregivers = body(mvc.perform(get("/api/patients/me/caregivers").header("Authorization", bearer(aminah))));
        String linkId = caregivers.get(0).get("linkId").asText();
        mvc.perform(delete("/api/patients/me/caregivers/{linkId}", linkId).header("Authorization", bearer(aminah))).andExpect(status().isOk());

        mvc.perform(get("/api/me").header("Authorization", bearer(nurul)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientIds").isEmpty());
        mvc.perform(get("/api/patients/{id}", patientId).header("Authorization", bearer(nurul))).andExpect(status().isForbidden());
    }

    @Test
    void aDoctorInvitesAColleagueToTheSameClinic() throws Exception {
        String code = body(postJson("/api/clinic/doctor-invites", doctor.getId(), "{}").andExpect(status().isCreated())).get("inviteCode").asText();
        UUID colleague = UUID.randomUUID();
        accept(code, colleague, "Dr Lim").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DOCTOR"));
        mvc.perform(get("/api/me").header("Authorization", bearer(colleague))).andExpect(jsonPath("$.clinicName").value("Klinik Dr Priya"));
    }

    @Test
    void anExistingPatientCannotAlsoBecomeADoctorThroughAnInvite() throws Exception {
        JsonNode registered = registerAminah();
        UUID aminah = UUID.randomUUID();
        accept(registered.get("inviteCode").asText(), aminah, "Aminah");
        String code = body(postJson("/api/clinic/doctor-invites", doctor.getId(), "{}")).get("inviteCode").asText();
        accept(code, aminah, "Aminah").andExpect(status().isConflict());
    }

    @Test
    void clinicStaffInvitationsCreateSeparateNurseAndAdministratorGrants() throws Exception {
        String nurseCode = body(postJson("/api/clinic/staff-invites", doctor.getId(), "{\"role\":\"NURSE\"}")
                .andExpect(status().isCreated())).get("inviteCode").asText();
        UUID nurse = UUID.randomUUID();
        accept(nurseCode, nurse, "Nurse Mei").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("NURSE"));
        mvc.perform(get("/api/me").header("Authorization", bearer(nurse)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clinicRoles[0]").value("NURSE"));
        mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(nurse))).andExpect(status().isOk());

        String adminCode = body(postJson("/api/clinic/staff-invites", doctor.getId(), "{\"role\":\"CLINIC_ADMIN\"}")
                .andExpect(status().isCreated())).get("inviteCode").asText();
        UUID admin = UUID.randomUUID();
        accept(adminCode, admin, "Clinic Admin").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CLINIC_ADMIN"));
        mvc.perform(get("/api/me").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clinicRoles[0]").value("CLINIC_ADMIN"));
        mvc.perform(get("/api/clinic/staff").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(admin))).andExpect(status().isForbidden());
        postJson("/api/clinic/staff-invites", admin, "{\"role\":\"DOCTOR\"}").andExpect(status().isForbidden());
    }

    @Test
    void theFirstClinicIsCreatedWithTheBootstrapToken() throws Exception {
        UUID founder = UUID.randomUUID();
        postJson("/api/onboarding/clinic", founder, "{\"bootstrapToken\":\"wrong\",\"clinicName\":\"Klinik Baru\",\"displayName\":\"Dr Aisyah\"}")
                .andExpect(status().isForbidden());
        postJson("/api/onboarding/clinic", founder, "{\"bootstrapToken\":\"test-bootstrap-token\",\"clinicName\":\"Klinik Baru\",\"displayName\":\"Dr Aisyah\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/me").header("Authorization", bearer(founder)))
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.clinicName").value("Klinik Baru"));
    }
}
