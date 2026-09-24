package com.khabar.api.followup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The follow-up case lifecycle, clinic settings and rota, the activity log and integration health. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FollowUpCaseTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired PatientReplyRepository replies;
    @Autowired ClinicStaffAccess staffAccess;
    @MockBean AgentClientService agents;

    AppUser doctor, nurse, admin, doctorElsewhere, rosnahAccount, aminahAccount;
    Patient rosnah, aminah;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Ujian"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        nurse = users.save(new AppUser(UUID.randomUUID(), Role.NURSE, "Nurse Mei", clinic));
        staffAccess.grant(nurse, clinic, ClinicStaffRole.NURSE, doctor.getId(), clock.instant());
        admin = users.save(new AppUser(UUID.randomUUID(), Role.CLINIC_ADMIN, "Admin Farah", clinic));
        staffAccess.grant(admin, clinic, ClinicStaffRole.CLINIC_ADMIN, doctor.getId(), clock.instant());
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        rosnahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Rosnah", null));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        rosnah = followedUp(new Patient(clinic, rosnahAccount, "Rosnah binti Ahmad", "620505-14-2222", "013-111 2222", "ms"));
        aminah = followedUp(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        Map<String, Object> healthy = new HashMap<>();
        healthy.put("status", "healthy");
        when(agents.checkAgentHealth()).thenReturn(healthy);
    }

    Patient followedUp(Patient p) {
        p.startFollowUp(LocalDate.now().minusDays(3));
        return patients.save(p);
    }

    void reply(AppUser as, String level, String text) throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", level, "matched", "x"));
        mvc.perform(post("/api/followup/replies").header("Authorization", bearer(as.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", text))))
                .andExpect(status().isOk());
    }

    JsonNode callList(AppUser as) throws Exception {
        return json.readTree(mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(as.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    JsonNode itemFor(JsonNode list, Patient p) {
        for (JsonNode item : list.get("items")) {
            if (item.get("patientId").asText().equals(p.getId().toString())) {
                return item;
            }
        }
        return null;
    }

    String caseId(AppUser as, Patient p) throws Exception {
        return itemFor(callList(as), p).get("followUpCase").get("id").asText();
    }

    ResultActions act(AppUser as, String caseId, String action, Object body) throws Exception {
        return mvc.perform(post("/api/clinic/cases/{id}/" + action, caseId).header("Authorization", bearer(as.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body == null ? Map.of() : body)));
    }

    JsonNode history(AppUser as, String caseId) throws Exception {
        return json.readTree(mvc.perform(get("/api/clinic/cases/{id}", caseId).header("Authorization", bearer(as.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    Map<String, Object> close(String reason, String note, JsonNode list) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        body.put("note", note);
        body.put("observedThrough", list.get("snapshotAt").asText());
        return body;
    }

    @Test
    void listingAPatientOpensOneCaseAndRefreshingNeverOpensAnother() throws Exception {
        reply(rosnahAccount, "red", "Sakit dada");
        JsonNode first = itemFor(callList(doctor), rosnah).get("followUpCase");
        assertThat(first.get("status").asText()).isEqualTo("NEW");
        assertThat(first.get("level").asText()).isEqualTo("RED");

        String again = caseId(nurse, rosnah);
        assertThat(again).isEqualTo(first.get("id").asText());
        assertThat(history(doctor, again).get("events")).hasSize(1);
    }

    @Test
    void assignAcknowledgeAndCallAttemptsAreRecordedOnceEach() throws Exception {
        reply(aminahAccount, "watch", "Pening");
        String id = caseId(doctor, aminah);

        act(doctor, id, "assign", Map.of("ownerId", nurse.getId())).andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.ownerName").value("Nurse Mei"));
        act(doctor, id, "assign", Map.of("ownerId", nurse.getId())).andExpect(status().isOk());
        act(nurse, id, "acknowledge", null).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        act(nurse, id, "acknowledge", null).andExpect(status().isOk());
        act(nurse, id, "contact", Map.of("outcome", "NO_ANSWER", "note", "Rang twice")).andExpect(jsonPath("$.status").value("UNABLE_TO_CONTACT"))
                .andExpect(jsonPath("$.contactAttempts").value(1));
        act(nurse, id, "contact", Map.of("outcome", "REACHED")).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        List<String> actions = new java.util.ArrayList<>();
        history(doctor, id).get("events").forEach(e -> actions.add(e.get("action").asText()));
        assertThat(actions).containsExactly("OPENED", "ASSIGNED", "ACKNOWLEDGED", "CONTACT_NO_ANSWER", "CONTACT_REACHED");
    }

    @Test
    void casesCanOnlyBeAssignedToFollowUpStaffAndOnlySeenInTheirClinic() throws Exception {
        reply(aminahAccount, "watch", "Pening");
        String id = caseId(doctor, aminah);
        act(doctor, id, "assign", Map.of("ownerId", admin.getId())).andExpect(status().isBadRequest());
        act(doctor, id, "assign", Map.of("ownerId", aminahAccount.getId())).andExpect(status().isBadRequest());
        act(doctorElsewhere, id, "acknowledge", null).andExpect(status().isNotFound());
        act(admin, id, "acknowledge", null).andExpect(status().isForbidden());
        act(aminahAccount, id, "acknowledge", null).andExpect(status().isForbidden());
    }

    @Test
    void anUrgentCaseNeedsANoteAndAnEscalationBeforeClosingAsUnreachable() throws Exception {
        reply(rosnahAccount, "red", "Sakit dada");
        JsonNode list = callList(nurse);
        String id = itemFor(list, rosnah).get("followUpCase").get("id").asText();

        act(nurse, id, "close", close("CONTACTED_NO_FURTHER_ACTION", "ok", list)).andExpect(status().isBadRequest());
        act(nurse, id, "close", close("UNABLE_TO_CONTACT_AFTER_ATTEMPTS", "No answer on three calls", list)).andExpect(status().isBadRequest());
        act(nurse, id, "contact", Map.of("outcome", "NO_ANSWER")).andExpect(status().isOk());
        act(nurse, id, "close", close("UNABLE_TO_CONTACT_AFTER_ATTEMPTS", "No answer on three calls", list)).andExpect(status().isConflict());
        act(nurse, id, "escalate", Map.of("note", "")).andExpect(status().isBadRequest());
        act(nurse, id, "escalate", Map.of("note", "Unreachable with chest pain", "toUserId", doctor.getId()))
                .andExpect(jsonPath("$.status").value("ESCALATED")).andExpect(jsonPath("$.escalatedTo").value("Dr Priya"));
        act(nurse, id, "close", close("UNABLE_TO_CONTACT_AFTER_ATTEMPTS", "No answer on three calls; doctor informed", list))
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        assertThat(itemFor(callList(nurse), rosnah)).isNull();
        assertThat(replies.findByPatientIdAndHandledAtIsNull(rosnah.getId())).isEmpty();
        act(nurse, id, "close", close("CONTACTED_NO_FURTHER_ACTION", "second time", list)).andExpect(status().isOk());
        assertThat(history(doctor, id).get("events").findValues("action").stream().filter(a -> a.asText().equals("CLOSED"))).hasSize(1);
    }

    @Test
    void aCaseStaysListedUntilClosedEvenWhenItsSourceHasCleared() throws Exception {
        reply(aminahAccount, "watch", "Pening");
        String id = caseId(doctor, aminah);
        replies.findByPatientIdAndHandledAtIsNull(aminah.getId()).forEach(r -> {
            r.markHandled(doctor.getId(), Instant.now());
            replies.save(r);
        });

        JsonNode item = itemFor(callList(doctor), aminah);
        assertThat(item.get("reason").asText()).isEqualTo("OPEN_CASE");
        assertThat(item.get("followUpCase").get("id").asText()).isEqualTo(id);
    }

    @Test
    void closingResolvesOnlyWhatWasOnScreenAndANewerReplyOpensANewCase() throws Exception {
        reply(aminahAccount, "watch", "Pening");
        JsonNode list = callList(doctor);
        String id = itemFor(list, aminah).get("followUpCase").get("id").asText();
        clock.advance(Duration.ofMinutes(1));
        reply(aminahAccount, "watch", "Masih pening");

        act(doctor, id, "close", close("ADVICE_GIVEN_PER_PROTOCOL", null, list)).andExpect(jsonPath("$.status").value("RESOLVED"));

        JsonNode reopened = itemFor(callList(doctor), aminah);
        assertThat(reopened).isNotNull();
        assertThat(reopened.get("followUpCase").get("id").asText()).isNotEqualTo(id);
        assertThat(reopened.get("latestReply").asText()).isEqualTo("Masih pening");
    }

    @Test
    void aCaseIsOverdueWhenNotAcknowledgedWithinTheClinicsTime() throws Exception {
        saveSettings(doctor, 5, 60, 120, List.of()).andExpect(status().isOk());
        reply(rosnahAccount, "red", "Sakit dada");
        String id = caseId(nurse, rosnah);
        assertThat(itemFor(callList(nurse), rosnah).get("followUpCase").get("overdue").asBoolean()).isFalse();

        clock.advance(Duration.ofMinutes(10));
        assertThat(itemFor(callList(nurse), rosnah).get("followUpCase").get("overdue").asBoolean()).isTrue();
        act(nurse, id, "acknowledge", null).andExpect(jsonPath("$.overdue").value(false));
    }

    ResultActions saveSettings(AppUser as, int red, int watch, int review, List<Map<String, Object>> rota) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("hours", "Mon–Fri 9am–5pm");
        body.put("escalationContact", "Dr Priya, 03-0000 0000");
        body.put("redAckMinutes", red);
        body.put("watchAckMinutes", watch);
        body.put("reviewAckMinutes", review);
        body.put("rota", rota);
        return mvc.perform(put("/api/clinic/settings").header("Authorization", bearer(as.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void managersSetTheRotaAndTheCallListShowsTodaysCover() throws Exception {
        String today = LocalDate.now(clock).getDayOfWeek().name();
        assertThat(callList(nurse).get("coverage").get("warning").asText()).contains("Nobody is rostered");

        saveSettings(admin, 30, 240, 1440, List.of(Map.of("day", today, "primaryUserId", nurse.getId(), "backupUserId", doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rota[0].primaryName").value("Nurse Mei"))
                .andExpect(jsonPath("$.today.onDuty").value("Nurse Mei"));

        JsonNode coverage = callList(doctor).get("coverage");
        assertThat(coverage.get("onDuty").asText()).isEqualTo("Nurse Mei");
        assertThat(coverage.get("backup").asText()).isEqualTo("Dr Priya");
        assertThat(coverage.get("warning").isNull()).isTrue();
        mvc.perform(get("/api/clinic/settings").header("Authorization", bearer(nurse.getId())))
                .andExpect(jsonPath("$.hours").value("Mon–Fri 9am–5pm"));
    }

    @Test
    void settingsAreValidatedAndOnlyManagersChangeThem() throws Exception {
        saveSettings(nurse, 30, 240, 1440, List.of()).andExpect(status().isForbidden());
        saveSettings(rosnahAccount, 30, 240, 1440, List.of()).andExpect(status().isForbidden());
        saveSettings(doctor, 1, 240, 1440, List.of()).andExpect(status().isBadRequest());
        saveSettings(doctor, 300, 240, 1440, List.of()).andExpect(status().isBadRequest());
        saveSettings(doctor, 30, 240, 1440, List.of(Map.of("day", "MONDAY", "primaryUserId", admin.getId()))).andExpect(status().isBadRequest());
        saveSettings(doctor, 30, 240, 1440, List.of(Map.of("day", "MONDAY", "primaryUserId", nurse.getId(), "backupUserId", nurse.getId())))
                .andExpect(status().isBadRequest());
        saveSettings(doctor, 30, 240, 1440, List.of(Map.of("day", "MONDAY", "primaryUserId", nurse.getId()),
                Map.of("day", "MONDAY", "primaryUserId", doctor.getId()))).andExpect(status().isBadRequest());
    }

    @Test
    void theActivityLogShowsWhoDidWhatWithoutNamingPatients() throws Exception {
        reply(rosnahAccount, "red", "Sakit dada");
        String id = caseId(nurse, rosnah);
        act(nurse, id, "acknowledge", null);
        saveSettings(admin, 30, 240, 1440, List.of()).andExpect(status().isOk());

        String body = mvc.perform(get("/api/clinic/activity").header("Authorization", bearer(admin.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("SETTINGS_UPDATED"))
                .andExpect(jsonPath("$[1].action").value("CASE_ACKNOWLEDGED"))
                .andExpect(jsonPath("$[1].by").value("Nurse Mei"))
                .andExpect(jsonPath("$[1].subject").value("Case " + id.substring(0, 8)))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Rosnah").doesNotContain("Sakit");
        mvc.perform(get("/api/clinic/activity").header("Authorization", bearer(nurse.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void integrationHealthIsShownToManagersOnly() throws Exception {
        mvc.perform(get("/api/clinic/integrations").header("Authorization", bearer(admin.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("AI agents"))
                .andExpect(jsonPath("$[0].status").value("OK"))
                .andExpect(jsonPath("$[1].status").value("LIMITED"));
        mvc.perform(get("/api/clinic/integrations").header("Authorization", bearer(nurse.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void theOlderRecordContactCallStillWorksAndClosesTheCase() throws Exception {
        reply(rosnahAccount, "red", "Sakit dada");
        JsonNode list = callList(doctor);
        String id = itemFor(list, rosnah).get("followUpCase").get("id").asText();
        mvc.perform(post("/api/clinic/call-list/{id}/called", rosnah.getId()).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("observedThrough", list.get("snapshotAt").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.handledReplies").value(1));
        assertThat(history(doctor, id).get("followUpCase").get("closureReason").asText()).isEqualTo("CONTACT_RECORDED");
        assertThat(itemFor(callList(doctor), rosnah)).isNull();
    }
}
