package com.khabar.api.reminders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.caregivers.CaregiverAlerts;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.encounters.EncounterRepository;
import com.khabar.api.encounters.TestVisits;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.Role;
import com.khabar.api.messaging.Messenger;
import com.khabar.api.messaging.TelegramBotClient;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.TelegramChatIndex;
import com.khabar.api.scheduling.Appointment;
import com.khabar.api.scheduling.AppointmentRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.encounters.TestVisits.rx;
import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dose, refill and appointment reminders, their buttons, no-shows and caregiver alerts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RemindersTest {

    static final long CHAT = 777001;
    static final long CAREGIVER_CHAT = 888002;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired ClinicStaffAccess staffAccess;
    @Autowired EncounterRepository encounters;
    @Autowired AppointmentRepository appointments;
    @Autowired CaregiverLinkRepository caregiverLinks;
    @Autowired TelegramChatIndex chatIndex;
    @Autowired DoseLogRepository doses;
    @Autowired Reminders reminders;
    @Autowired AuditLog auditLog;
    @MockBean TelegramBotClient bot;
    @MockBean AgentClientService agents;

    Clinic clinic;
    AppUser doctor, aminahAccount, caregiver;
    Patient aminah;

    @BeforeEach
    void setUp() {
        clock.reset();
        when(bot.configured()).thenReturn(true);
        when(bot.sendMessage(anyLong(), anyString(), any())).thenReturn(Messenger.Result.ok("1"));
        when(bot.sendMessage(anyLong(), anyString())).thenReturn(Messenger.Result.ok("1"));
        clinic = clinics.save(new Clinic("Klinik Peringatan"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        Patient p = new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms");
        String chat = Long.toString(CHAT + System.nanoTime() % 100000);
        p.linkTelegram(chat, chatIndex.of(chat));
        aminah = patients.save(p);
        caregiver = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Siti", null));
    }

    private long chat() {
        return Long.parseLong(aminah.getTelegramChatId());
    }

    /** Moves the clock forward to the next time the local clock shows this time. */
    private void advanceTo(LocalTime time) {
        ZonedDateTime now = clock.instant().atZone(clock.getZone());
        ZonedDateTime target = now.toLocalDate().atTime(time).atZone(clock.getZone());
        if (!target.isAfter(now)) {
            target = target.plusDays(1);
        }
        clock.advance(Duration.between(now, target));
    }

    private void signVisit(boolean fasting, Double weeks) {
        encounters.save(TestVisits.signed(aminah, doctor, clock.instant(), fasting, weeks, List.of(
                rx("Metformin", 500, List.of("morning", "night"), "after_food", false),
                rx("Amlodipine", 5, List.of("morning"), null, false),
                rx("Paracetamol", 500, List.of("morning"), null, true))));
    }

    private void switchOn() throws Exception {
        mvc.perform(put("/api/me/reminders").header("Authorization", bearer(aminahAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"doseReminders\":true,\"appointmentReminders\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doseReminders").value(true))
                .andExpect(jsonPath("$.telegramLinked").value(true));
    }

    @SuppressWarnings("unchecked")
    private String lastButtonData(int index) {
        ArgumentCaptor<Map<String, Object>> markup = ArgumentCaptor.forClass(Map.class);
        verify(bot, atLeastOnce()).sendMessage(eq(chat()), anyString(), markup.capture());
        Map<String, Object> last = markup.getValue();
        List<List<Map<String, Object>>> rows = (List<List<Map<String, Object>>>) last.get("inline_keyboard");
        return rows.get(0).get(index).get("callback_data").toString();
    }

    private void tap(long fromChat, String data) throws Exception {
        telegram(Map.of("callback_query", Map.of("id", "cb-" + UUID.randomUUID(), "data", data,
                "from", Map.of("id", fromChat),
                "message", Map.of("message_id", 55, "chat", Map.of("id", fromChat, "type", "private")))));
    }

    private void telegram(Map<String, Object> update) throws Exception {
        mvc.perform(post("/api/webhooks/telegram").header("X-Telegram-Bot-Api-Secret-Token", "test-telegram-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk());
    }

    @Test
    void remindersStayOffUntilThePatientSwitchesThemOn() throws Exception {
        signVisit(false, 4.0);
        advanceTo(LocalTime.of(8, 30));
        reminders.runDoses();
        assertThat(doses.findAll()).noneMatch(d -> d.getPatientId().equals(aminah.getId()));
        mvc.perform(get("/api/me/reminders").header("Authorization", bearer(doctor.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void aDoseReminderListsThatTimesMedicinesOnceWithButtons() throws Exception {
        signVisit(false, 4.0);
        switchOn();
        advanceTo(LocalTime.of(8, 30));

        assertThat(reminders.runDoses()).isEqualTo(1);
        assertThat(reminders.runDoses()).as("never twice for the same time of day").isZero();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(eq(chat()), text.capture(), any());
        assertThat(text.getValue()).startsWith("⏰ Masa makan ubat (pagi):")
                .contains("Metformin 500 mg — 1 biji, selepas makan").contains("Amlodipine 5 mg")
                .as("as-needed medicines get no reminder").doesNotContain("Paracetamol");
        assertThat(lastButtonData(0)).startsWith("d:").endsWith(":T");
        assertThat(lastButtonData(1)).endsWith(":M");
    }

    @Test
    void thePatientSeesTodaysMedicinesByTimeOfDay() throws Exception {
        signVisit(false, 4.0);
        mvc.perform(get("/api/me/medicines-today").header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slot").value("morning"))
                .andExpect(jsonPath("$[0].medicines.length()").value(2))
                .andExpect(jsonPath("$[0].medicines[0].timing").value("after_food"))
                .andExpect(jsonPath("$[1].slot").value("night"));
    }

    @Test
    void aLateServerDoesNotSendAReminderHoursAfterItsTime() throws Exception {
        signVisit(false, 4.0);
        switchOn();
        advanceTo(LocalTime.of(11, 30));
        assertThat(reminders.runDoses()).isZero();
    }

    @Test
    void whileFastingMorningDosesMoveToSahur() throws Exception {
        signVisit(true, 4.0);
        switchOn();
        advanceTo(LocalTime.of(5, 10));
        assertThat(reminders.runDoses()).isEqualTo(1);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(eq(chat()), text.capture(), any());
        assertThat(text.getValue()).startsWith("⏰ Masa makan ubat (sahur):");
    }

    @Test
    void onlyThePatientsOwnChatCanAnswerAndTheFirstAnswerCounts() throws Exception {
        signVisit(false, 4.0);
        switchOn();
        advanceTo(LocalTime.of(8, 30));
        reminders.runDoses();
        String taken = lastButtonData(0);
        String missed = lastButtonData(1);

        tap(123456, taken);
        assertThat(doses.findAll()).filteredOn(d -> d.getPatientId().equals(aminah.getId())).allMatch(d -> d.getResponse() == null);

        tap(chat(), taken);
        tap(chat(), missed);
        assertThat(doses.findAll()).filteredOn(d -> d.getPatientId().equals(aminah.getId()))
                .extracting(DoseLog::getResponse).containsExactly("TAKEN");
        verify(bot).removeButtons(chat(), 55);
        verify(bot).sendMessage(chat(), "Terima kasih, direkodkan.");

        mvc.perform(get("/api/me/reminders").header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adherence.sent").value(1))
                .andExpect(jsonPath("$.adherence.taken").value(1));
    }

    @Test
    void twoMissedDosesPutThePatientOnTheCallListAndTellTheCaregiver() throws Exception {
        caregiverLinks.save(new CaregiverLink(aminah, caregiver, CaregiverScope.SUMMARY_AND_ALERTS));
        String code = json.readTree(mvc.perform(post("/api/me/caregiver-telegram/link-code").header("Authorization", bearer(caregiver.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("code").asText();
        long caregiverChat = CAREGIVER_CHAT + System.nanoTime() % 100000;
        telegram(Map.of("message", Map.of("message_id", 1, "text", "/link " + code,
                "chat", Map.of("id", caregiverChat, "type", "private"), "from", Map.of("id", caregiverChat))));
        verify(bot).sendMessage(caregiverChat, CaregiverAlerts.LINKED_TEXT);

        signVisit(false, 4.0);
        switchOn();
        advanceTo(LocalTime.of(8, 30));
        reminders.runDoses();
        tap(chat(), lastButtonData(1));
        verify(bot, never()).sendMessage(eq(caregiverChat), org.mockito.ArgumentMatchers.contains("terlepas"));

        advanceTo(LocalTime.of(21, 15));
        reminders.runDoses();
        // The patient answers in the app this time.
        UUID nightDose = doses.findAll().stream().filter(d -> d.getPatientId().equals(aminah.getId()) && d.getSlot().equals("night"))
                .findFirst().orElseThrow().getId();
        mvc.perform(post("/api/me/doses/{id}", nightDose).header("Authorization", bearer(aminahAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"MISSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Terima kasih kerana memberitahu")));

        JsonNode list = json.readTree(mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(list.get("items").get(0).get("reason").asText()).isEqualTo("MISSED_DOSE");
        verify(bot).sendMessage(eq(caregiverChat), org.mockito.ArgumentMatchers.contains("reported missing medicine twice"));

        mvc.perform(get("/api/me/caregiver-telegram").header("Authorization", bearer(caregiver.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.recent[0].kind").value("MISSED_DOSES"));
    }

    @Test
    void theClinicSeesAdherenceAndTheViewIsAudited() throws Exception {
        signVisit(false, 4.0);
        switchOn();
        advanceTo(LocalTime.of(8, 30));
        reminders.runDoses();
        mvc.perform(get("/api/patients/{id}/adherence", aminah.getId()).header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(1))
                .andExpect(jsonPath("$.unanswered").value(1));
        assertThat(auditLog.forPatient(aminah.getId())).anyMatch(e -> e.getAction() == AuditAction.VIEWED_RECORD);
        mvc.perform(get("/api/patients/{id}/adherence", aminah.getId()).header("Authorization", bearer(caregiver.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void theEveningBeforeAVisitThePatientCanSayTheyCannotComeWhichFreesTheSlot() throws Exception {
        switchOn();
        advanceTo(LocalTime.of(18, 30));
        Instant tomorrow = clock.instant().atZone(clock.getZone()).toLocalDate().plusDays(1).atTime(10, 0).atZone(clock.getZone()).toInstant();
        Appointment booked = appointments.save(new Appointment(clinic, aminah, tomorrow, null, aminahAccount.getId(), clock.instant()));

        assertThat(reminders.runAppointmentReminders()).isEqualTo(1);
        assertThat(reminders.runAppointmentReminders()).isZero();
        tap(chat(), lastButtonData(1));

        assertThat(appointments.findById(booked.getId()).orElseThrow().getStatus()).isEqualTo(Appointment.Status.CANCELLED);
        verify(bot).sendMessage(chat(), "Temu janji anda telah dibatalkan dan masa itu dibuka semula. Pilih masa baharu dalam Khabar.");
    }

    @Test
    void aMissedVisitGoesOnTheCallListUntilContactIsRecorded() throws Exception {
        Instant startsAt = clock.instant().plus(Duration.ofHours(2));
        appointments.save(new Appointment(clinic, aminah, startsAt, null, aminahAccount.getId(), clock.instant()));
        assertThat(reminders.detectNoShows()).isZero();
        clock.advance(Duration.ofHours(4).plusMinutes(5));
        assertThat(reminders.detectNoShows()).isEqualTo(1);
        assertThat(reminders.detectNoShows()).isZero();

        JsonNode list = json.readTree(mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode item = list.get("items").get(0);
        assertThat(item.get("reason").asText()).isEqualTo("MISSED_APPOINTMENT");
        String snapshot = list.get("snapshotAt").asText();

        mvc.perform(post("/api/clinic/call-list/{id}/called", aminah.getId()).header("Authorization", bearer(doctor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"observedThrough\":\"" + snapshot + "\"}"))
                .andExpect(status().isOk());
        JsonNode after = json.readTree(mvc.perform(get("/api/clinic/call-list").header("Authorization", bearer(doctor.getId())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(after.get("items")).isEmpty();
    }

    @Test
    void aVisitStartedThatDayIsNotANoShow() {
        Instant startsAt = clock.instant().plus(Duration.ofMinutes(30));
        appointments.save(new Appointment(clinic, aminah, startsAt, null, aminahAccount.getId(), clock.instant()));
        encounters.save(new com.khabar.api.encounters.Encounter(aminah, doctor, startsAt.plus(Duration.ofMinutes(5))));
        clock.advance(Duration.ofHours(3));
        assertThat(reminders.detectNoShows()).isZero();
    }

    @Test
    void anUrgentReplyAlertsCaregiversWhoShareAlertsButNotSummaryOnlyOnes() throws Exception {
        AppUser summaryOnly = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Ali", null));
        caregiverLinks.save(new CaregiverLink(aminah, caregiver, CaregiverScope.SUMMARY_AND_ALERTS));
        caregiverLinks.save(new CaregiverLink(aminah, summaryOnly, CaregiverScope.SUMMARY));
        long alertsChat = linkCaregiver(caregiver);
        long summaryChat = linkCaregiver(summaryOnly);

        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "red", "matched", "sakit dada"));
        mvc.perform(post("/api/followup/replies").header("Authorization", bearer(aminahAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", "sakit dada sejak pagi"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot, atLeastOnce()).sendMessage(eq(alertsChat), text.capture());
        assertThat(text.getAllValues()).anyMatch(t -> t.contains("may be urgent") && t.contains("999") && !t.contains("sakit dada"));
        verify(bot, never()).sendMessage(eq(summaryChat), org.mockito.ArgumentMatchers.contains("urgent"));
    }

    @Test
    void aCaregiverChatThatIsNotAPatientIsToldItGetsAlertsOnly() throws Exception {
        long chat = linkCaregiver(caregiver);
        telegram(Map.of("message", Map.of("message_id", 2, "text", "hello",
                "chat", Map.of("id", chat, "type", "private"), "from", Map.of("id", chat))));
        verify(bot).sendMessage(chat, CaregiverAlerts.ALERTS_ONLY);
        mvc.perform(post("/api/me/caregiver-telegram/link-code").header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isForbidden());
    }

    private long linkCaregiver(AppUser who) throws Exception {
        String code = json.readTree(mvc.perform(post("/api/me/caregiver-telegram/link-code").header("Authorization", bearer(who.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("code").asText();
        long chat = 900000 + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000);
        telegram(Map.of("message", Map.of("message_id", 1, "text", "/link " + code,
                "chat", Map.of("id", chat, "type", "private"), "from", Map.of("id", chat))));
        return chat;
    }
}
