package com.khabar.api.messaging;

import com.khabar.api.encounters.VisitSummary;
import com.khabar.api.encounters.VisitSummaryRepository;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.TelegramChatIndex;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Telegram's webhook: secret-authenticated updates that link a chat by shared number, then carry replies. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TelegramWebhookTest {

    static final String SECRET = "test-telegram-secret";

    @Autowired MockMvc mvc;
    @Autowired ClinicRepository clinics;
    @Autowired PatientRepository patients;
    @Autowired PatientReplyRepository replies;
    @Autowired OutboundMessageRepository outbox;
    @Autowired VisitSummaryRepository summaries;
    @Autowired TelegramChatIndex chatIndex;
    @Autowired com.khabar.api.medications.MedicationItemRepository medications;
    @MockBean AgentClientService agents;
    @MockBean TelegramBotClient bot;

    Clinic clinic;
    Patient aminah;
    long chat;

    @BeforeEach
    void setUp() {
        clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        aminah = patients.save(new Patient(clinic, null, "Aminah binti Yusof", "590312-10-5566", uniqueLocalPhone(), "ms"));
        chat = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "watch", "matched", "pening"));
        when(bot.sendMessage(anyLong(), anyString(), any())).thenReturn(Messenger.Result.ok("1"));
    }

    static String uniqueLocalPhone() {
        return "01" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
    }

    /** Telegram's form of a clinic-typed number: +60 instead of the leading 0. */
    static String telegramForm(String local) {
        return "+6" + local.replaceAll("\\D", "");
    }

    static String message(long chatId, long fromId, String chatType, int messageId, String fields) {
        return """
                {"update_id":%d,"message":{"message_id":%d,"from":{"id":%d,"is_bot":false,"first_name":"A"},
                "chat":{"id":%d,"type":"%s"},"date":1,%s}}
                """.formatted(messageId, messageId, fromId, chatId, chatType, fields);
    }

    String text(int messageId, String text) {
        return message(chat, chat, "private", messageId, "\"text\":\"" + text + "\"");
    }

    String contact(long fromId, long contactUserId, String phone) {
        return message(chat, fromId, "private", 7, "\"contact\":{\"phone_number\":\"%s\",\"first_name\":\"A\",\"user_id\":%d}"
                .formatted(phone, contactUserId));
    }

    ResultActions deliver(String body, String secret) throws Exception {
        var request = post("/api/webhooks/telegram").contentType(MediaType.APPLICATION_JSON).content(body);
        if (secret != null) {
            request.header("X-Telegram-Bot-Api-Secret-Token", secret);
        }
        return mvc.perform(request);
    }

    void link(Patient p) {
        p.linkTelegram(String.valueOf(chat), chatIndex.of(String.valueOf(chat)));
        patients.save(p);
    }

    List<OutboundMessage> sentTo(Patient p, Messenger.Kind kind) {
        return outbox.findAll().stream().filter(m -> m.getPatientId().equals(p.getId()) && m.getKind().equals(kind.name())).toList();
    }

    @Test
    void aWrongOrMissingSecretIsRefused() throws Exception {
        deliver(text(1, "Pening"), "wrong").andExpect(status().isUnauthorized());
        deliver(text(1, "Pening"), null).andExpect(status().isUnauthorized());
        verify(bot, never()).sendMessage(anyLong(), anyString(), any());
    }

    @Test
    void startShowsTheShareNumberButton() throws Exception {
        deliver(text(1, "/start"), SECRET).andExpect(status().isOk());
        verify(bot).sendMessage(eq(chat), eq(TelegramWebhookController.START),
                argThat(markup -> markup != null && markup.toString().contains("request_contact=true")));
    }

    @Test
    void ownContactLinksAndSendsTheLatestSummary() throws Exception {
        summaries.save(new VisitSummary(aminah, UUID.randomUUID(), "ms", "• Metformin 500 mg: 1 biji, pagi dan malam.", null, Instant.now()));

        deliver(contact(chat, chat, telegramForm(aminah.getPhone())), SECRET).andExpect(status().isOk());

        Patient linked = patients.findById(aminah.getId()).orElseThrow();
        assertThat(linked.getTelegramChatId()).isEqualTo(String.valueOf(chat));
        assertThat(patients.findFirstByTelegramChatIndex(chatIndex.of(String.valueOf(chat)))).get()
                .extracting(Patient::getId).isEqualTo(aminah.getId());
        verify(bot).sendMessage(chat, PatientMessages.telegramLinkedText("ms"), TelegramWebhookController.REMOVE_KEYBOARD);
        assertThat(sentTo(aminah, Messenger.Kind.SUMMARY)).extracting(OutboundMessage::getText)
                .containsExactly("• Metformin 500 mg: 1 biji, pagi dan malam.");
    }

    @Test
    void someoneElsesContactIsRefused() throws Exception {
        deliver(contact(chat, chat + 1, telegramForm(aminah.getPhone())), SECRET).andExpect(status().isOk());
        assertThat(patients.findById(aminah.getId()).orElseThrow().getTelegramChatId()).isNull();
        verify(bot).sendMessage(eq(chat), eq(TelegramWebhookController.NOT_OWN), any());
    }

    @Test
    void anUnknownNumberIsNotLinked() throws Exception {
        deliver(contact(chat, chat, "+60199999999"), SECRET).andExpect(status().isOk());
        verify(bot).sendMessage(chat, TelegramWebhookController.NOT_FOUND, TelegramWebhookController.REMOVE_KEYBOARD);
    }

    @Test
    void aNumberRegisteredForTwoPatientsLinksNeither() throws Exception {
        Patient sibling = patients.save(new Patient(clinic, null, "Siti binti Yusof", "600101-10-1111", aminah.getPhone(), "ms"));
        deliver(contact(chat, chat, telegramForm(aminah.getPhone())), SECRET).andExpect(status().isOk());
        assertThat(patients.findById(aminah.getId()).orElseThrow().getTelegramChatId()).isNull();
        assertThat(patients.findById(sibling.getId()).orElseThrow().getTelegramChatId()).isNull();
        verify(bot).sendMessage(chat, TelegramWebhookController.AMBIGUOUS, TelegramWebhookController.REMOVE_KEYBOARD);
    }

    @Test
    void relinkingAChatToAnotherPatientUnlinksTheFirst() throws Exception {
        Patient other = patients.save(new Patient(clinic, null, "Tan Kok Hoe", "540101-07-1234", uniqueLocalPhone(), "zh"));
        link(other);
        deliver(contact(chat, chat, telegramForm(aminah.getPhone())), SECRET).andExpect(status().isOk());
        assertThat(patients.findById(other.getId()).orElseThrow().getTelegramChatId()).isNull();
        assertThat(patients.findById(aminah.getId()).orElseThrow().getTelegramChatId()).isEqualTo(String.valueOf(chat));
    }

    @Test
    void aLinkedPatientsTextBecomesATriagedReply() throws Exception {
        link(aminah);
        deliver(text(11, "Pening dan berpeluh"), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).anyMatch(r -> r.getPatient().getId().equals(aminah.getId())
                && r.getLevel() == TriageLevel.WATCH && r.getText().equals("Pening dan berpeluh"));
        assertThat(sentTo(aminah, Messenger.Kind.NOTICE)).hasSize(1);
    }

    @Test
    void theSameUpdateDeliveredTwiceIsTriagedOnce() throws Exception {
        link(aminah);
        deliver(text(12, "Pening"), SECRET).andExpect(status().isOk());
        deliver(text(12, "Pening"), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).filteredOn(r -> r.getPatient().getId().equals(aminah.getId())).hasSize(1);
        verify(agents, times(1)).triageReply(anyString(), any());
        assertThat(sentTo(aminah, Messenger.Kind.NOTICE)).hasSize(1);
    }

    @Test
    void aPhotoThatCannotBeReadAsksForABetterPhotoOrTheName() throws Exception {
        link(aminah);
        deliver(message(chat, chat, "private", 13, "\"photo\":[{\"file_id\":\"x\",\"width\":1,\"height\":1}]"), SECRET)
                .andExpect(status().isOk());
        assertThat(replies.findAll()).noneMatch(r -> r.getPatient().getId().equals(aminah.getId()));
        verify(bot).sendMessage(chat, PatientMessages.photoUnreadText("ms"));
    }

    @Test
    void aPacketPhotoIsReadIntoTheMedicineListForTheClinicToVerify() throws Exception {
        link(aminah);
        when(bot.downloadFile("big")).thenReturn(new byte[]{1, 2, 3});
        when(agents.readMedicinePacket(any(), anyString())).thenReturn(Map.of("candidates",
                List.of(Map.of("kind", "MEDICINE", "brand", "Glucophage", "strength", "500 mg"))));
        deliver(message(chat, chat, "private", 14,
                "\"photo\":[{\"file_id\":\"small\",\"width\":1,\"height\":1},{\"file_id\":\"big\",\"width\":9,\"height\":9}]"), SECRET)
                .andExpect(status().isOk());
        assertThat(medications.findByPatientIdOrderByAddedAt(aminah.getId())).singleElement().satisfies(item -> {
            assertThat(item.getName()).isEqualTo("Glucophage 500 mg");
            assertThat(item.getSource()).isEqualTo(TelegramMedia.PHOTO_SOURCE);
        });
        verify(bot).sendMessage(chat, PatientMessages.photoReadText("ms", "Glucophage 500 mg"));
    }

    @Test
    void aVoiceNoteIsShownBackAndOnlyBecomesAReplyWhenThePatientTapsSend() throws Exception {
        link(aminah);
        when(bot.downloadFile("v1")).thenReturn(new byte[]{1, 2, 3});
        when(agents.transcribe(any(), anyString(), anyString())).thenReturn("Pening sikit lepas makan ubat");
        deliver(message(chat, chat, "private", 15, "\"voice\":{\"file_id\":\"v1\",\"duration\":3}"), SECRET)
                .andExpect(status().isOk());
        assertThat(replies.findAll()).noneMatch(r -> r.getPatient().getId().equals(aminah.getId()));
        verify(bot).sendMessage(eq(chat), eq(PatientMessages.voiceHeardText("ms", "Pening sikit lepas makan ubat")),
                argThat(markup -> markup != null && markup.toString().contains(TelegramMedia.SEND_BUTTON)));

        deliver(text(16, TelegramMedia.SEND_BUTTON), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).filteredOn(r -> r.getPatient().getId().equals(aminah.getId()))
                .singleElement().satisfies(r -> assertThat(r.getText()).isEqualTo("Pening sikit lepas makan ubat"));
    }

    @Test
    void typingInsteadDiscardsTheVoiceTranscript() throws Exception {
        link(aminah);
        when(bot.downloadFile("v2")).thenReturn(new byte[]{1});
        when(agents.transcribe(any(), anyString(), anyString())).thenReturn("Salah dengar");
        deliver(message(chat, chat, "private", 17, "\"voice\":{\"file_id\":\"v2\",\"duration\":2}"), SECRET).andExpect(status().isOk());
        deliver(text(18, "Pening"), SECRET).andExpect(status().isOk());
        deliver(text(19, TelegramMedia.SEND_BUTTON), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).filteredOn(r -> r.getPatient().getId().equals(aminah.getId()))
                .extracting(r -> r.getText()).containsExactly("Pening");
    }

    @Test
    void aStartCommandFromALinkedPatientIsNotAReply() throws Exception {
        link(aminah);
        deliver(text(14, "/start"), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).noneMatch(r -> r.getPatient().getId().equals(aminah.getId()));
        assertThat(sentTo(aminah, Messenger.Kind.NOTICE)).extracting(OutboundMessage::getText)
                .containsExactly(PatientMessages.telegramLinkedText("ms"));
    }

    @Test
    void anUnlinkedChatsTextGetsTheShareNumberPrompt() throws Exception {
        deliver(text(15, "Pening"), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).noneMatch(r -> r.getText().equals("Pening") && r.getPatient().getId().equals(aminah.getId()));
        verify(bot).sendMessage(eq(chat), eq(TelegramWebhookController.START), any());
    }

    @Test
    void groupChatsAreIgnored() throws Exception {
        link(aminah);
        deliver(message(chat, chat, "group", 16, "\"text\":\"Pening\""), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).noneMatch(r -> r.getPatient().getId().equals(aminah.getId()));
        verify(bot, never()).sendMessage(anyLong(), anyString(), any());
    }

    @Test
    void updatesWithoutAMessageAreAcknowledgedAndIgnored() throws Exception {
        deliver("{\"update_id\":99,\"edited_message\":{\"message_id\":1}}", SECRET).andExpect(status().isOk());
        verify(bot, never()).sendMessage(anyLong(), anyString(), any());
    }
}
