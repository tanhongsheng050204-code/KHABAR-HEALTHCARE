package com.khabar.api.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.encounters.VisitSummaryRepository;
import com.khabar.api.followup.FollowUpService;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.PhoneIndex;
import com.khabar.api.patients.TelegramChatIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Telegram's webhook. Each delivery carries the secret given to setWebhook in
 * X-Telegram-Bot-Api-Secret-Token, which is what authenticates it. A patient links their chat by
 * sharing their own, Telegram-verified phone number; after that their texts are follow-up replies.
 */
@RestController
@RequestMapping("/api/webhooks/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    static final String SHARE_BUTTON = "📱 Kongsi nombor telefon saya / Share my phone number";
    static final String START = "Selamat datang ke Khabar. Tekan butang di bawah untuk berkongsi nombor telefon anda supaya klinik "
            + "boleh menghantar pelan rawatan anda.\n\nWelcome to Khabar. Tap the button below to share your phone number so "
            + "your clinic can send you your care plan.";
    static final String NOT_OWN = "Sila kongsi nombor anda sendiri menggunakan butang di bawah.\n\n"
            + "Please share your own number using the button below.";
    static final String NOT_FOUND = "Kami tidak menjumpai rekod klinik untuk nombor ini. Sila hubungi klinik anda.\n\n"
            + "We couldn't find a clinic record for this number. Please ask your clinic.";
    static final String AMBIGUOUS = "Nombor ini didaftarkan untuk lebih daripada seorang pesakit. Sila minta klinik anda mengemas kini rekod.\n\n"
            + "This number is registered for more than one patient. Please ask your clinic to update the record.";
    static final Map<String, Object> REMOVE_KEYBOARD = Map.of("remove_keyboard", true);
    static final Map<String, Object> SHARE_KEYBOARD = Map.of(
            "keyboard", List.of(List.of(Map.of("text", SHARE_BUTTON, "request_contact", true))),
            "one_time_keyboard", true, "resize_keyboard", true);

    private final String secret;
    private final ObjectMapper json;
    private final PatientRepository patients;
    private final PhoneIndex phoneIndex;
    private final TelegramChatIndex chatIndex;
    private final FollowUpService followUp;
    private final PatientMessages messages;
    private final VisitSummaryRepository summaries;
    private final TelegramBotClient bot;

    public TelegramWebhookController(@Value("${khabar.telegram.webhook-secret:}") String secret, ObjectMapper json,
                                     PatientRepository patients, PhoneIndex phoneIndex, TelegramChatIndex chatIndex,
                                     FollowUpService followUp, PatientMessages messages, VisitSummaryRepository summaries,
                                     TelegramBotClient bot) {
        this.secret = secret;
        this.json = json;
        this.patients = patients;
        this.phoneIndex = phoneIndex;
        this.chatIndex = chatIndex;
        this.followUp = followUp;
        this.messages = messages;
        this.summaries = summaries;
        this.bot = bot;
    }

    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@RequestBody String body,
                          @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String token) {
        if (secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram webhook is not configured.");
        }
        if (token == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad secret.");
        }
        JsonNode update;
        try {
            update = json.readTree(body);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not JSON.");
        }
        try {
            handle(update.path("message"));
        } catch (RuntimeException e) {
            log.warn("Telegram update not handled: {}", e.getClass().getSimpleName());
        }
        // Telegram retries anything that is not 200, so problems are logged, not returned.
        return "OK";
    }

    private void handle(JsonNode message) {
        if (message.isMissingNode() || !"private".equals(message.path("chat").path("type").asText())) {
            return;
        }
        String chatId = message.path("chat").path("id").asText();
        if (chatId.isBlank()) {
            return;
        }
        if (message.has("contact")) {
            link(message, chatId);
            return;
        }
        Optional<Patient> patient = patients.findFirstByTelegramChatIndex(chatIndex.of(chatId));
        if (patient.isEmpty()) {
            bot.sendMessage(Long.parseLong(chatId), START, SHARE_KEYBOARD);
            return;
        }
        String language = patient.get().getPreferredLanguage();
        String text = message.path("text").asText("");
        if (text.isBlank()) {
            messages.send(patient.get(), PatientMessages.textOnlyText(language), Messenger.Kind.NOTICE);
            return;
        }
        if (text.startsWith("/")) {
            messages.send(patient.get(), PatientMessages.telegramLinkedText(language), Messenger.Kind.NOTICE);
            return;
        }
        UUID clientMessageId = UUID.nameUUIDFromBytes(
                ("telegram:" + chatId + ":" + message.path("message_id").asText()).getBytes(StandardCharsets.UTF_8));
        followUp.receiveReply(patient.get(), text, clientMessageId);
    }

    private void link(JsonNode message, String chatId) {
        long chat = Long.parseLong(chatId);
        JsonNode contact = message.path("contact");
        String sender = message.path("from").path("id").asText();
        if (sender.isBlank() || !sender.equals(contact.path("user_id").asText())) {
            bot.sendMessage(chat, NOT_OWN, SHARE_KEYBOARD);
            return;
        }
        List<Patient> matches = patients.findByPhoneIndex(phoneIndex.of(contact.path("phone_number").asText()));
        if (matches.isEmpty()) {
            bot.sendMessage(chat, NOT_FOUND, REMOVE_KEYBOARD);
            return;
        }
        if (matches.size() > 1) {
            log.info("A shared number matches {} patients; not linked.", matches.size());
            bot.sendMessage(chat, AMBIGUOUS, REMOVE_KEYBOARD);
            return;
        }
        Patient patient = matches.get(0);
        String index = chatIndex.of(chatId);
        for (Patient previous : patients.findByTelegramChatIndex(index)) {
            if (!previous.getId().equals(patient.getId())) {
                previous.unlinkTelegram();
                patients.save(previous);
            }
        }
        patient.linkTelegram(chatId, index);
        Patient saved = patients.save(patient);
        bot.sendMessage(chat, PatientMessages.telegramLinkedText(saved.getPreferredLanguage()), REMOVE_KEYBOARD);
        summaries.findFirstByPatientIdOrderByCreatedAtDesc(saved.getId())
                .ifPresent(summary -> messages.send(saved, summary.getText(), Messenger.Kind.SUMMARY));
    }
}
