package com.khabar.api.messaging;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.FollowUpService;
import com.khabar.api.graph.PatientGraphSync;
import com.khabar.api.identity.Role;
import com.khabar.api.medications.MedicationItem;
import com.khabar.api.medications.MedicationItemRepository;
import com.khabar.api.medications.MedicationPacketController;
import com.khabar.api.patients.Patient;
import com.khabar.api.privacy.AiReceipts;
import com.khabar.api.service.AgentClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Voice notes and medicine-packet photos sent to the Telegram bot. A voice note is transcribed and shown back to
 * the patient; only when they tap Send does it become a reply. A packet photo is read only after the patient taps
 * Read label, and what was read is added to the medicine list marked "please verify", for the clinic to check.
 * Neither file is kept.
 */
@Service
public class TelegramMedia {

    private static final Logger log = LoggerFactory.getLogger(TelegramMedia.class);

    public static final String SEND_BUTTON = "✅ Hantar / Send";
    static final Map<String, Object> SEND_KEYBOARD = Map.of(
            "keyboard", List.of(List.of(Map.of("text", SEND_BUTTON))),
            "one_time_keyboard", true, "resize_keyboard", true);
    static final Map<String, Object> REMOVE_KEYBOARD = Map.of("remove_keyboard", true);
    public static final String READ_BUTTON = "📷 Baca label / Read label";
    public static final String MEAL_BUTTON = "🍽️ Semak makanan / Check my meal";
    static final Map<String, Object> READ_KEYBOARD = Map.of(
            "keyboard", List.of(List.of(Map.of("text", READ_BUTTON)), List.of(Map.of("text", MEAL_BUTTON))),
            "one_time_keyboard", true, "resize_keyboard", true);
    /** A transcript left unconfirmed this long is no longer offered. */
    static final Duration DRAFT_LIFETIME = Duration.ofHours(6);
    static final String PHOTO_SOURCE = "Packet photo via Telegram — please verify";

    private final TelegramBotClient bot;
    private final AgentClientService agents;
    private final AiReceipts aiReceipts;
    private final VoiceDraftRepository drafts;
    private final PhotoDraftRepository photoDrafts;
    private final FollowUpService followUp;
    private final MedicationItemRepository medications;
    private final PatientGraphSync graphSync;
    private final AdjustableClock clock;
    @org.springframework.beans.factory.annotation.Autowired
    private com.khabar.api.meals.MealAdvice mealAdvice;

    public TelegramMedia(TelegramBotClient bot, AgentClientService agents, AiReceipts aiReceipts, VoiceDraftRepository drafts,
                         PhotoDraftRepository photoDrafts, FollowUpService followUp, MedicationItemRepository medications, PatientGraphSync graphSync, AdjustableClock clock) {
        this.bot = bot;
        this.agents = agents;
        this.aiReceipts = aiReceipts;
        this.drafts = drafts;
        this.photoDrafts = photoDrafts;
        this.followUp = followUp;
        this.medications = medications;
        this.graphSync = graphSync;
        this.clock = clock;
    }

    @Transactional
    public void voice(Patient patient, long chatId, String fileId, String messageId) {
        String language = patient.getPreferredLanguage();
        byte[] audio = bot.downloadFile(fileId);
        String transcript = null;
        if (audio != null) {
            aiReceipts.media(patient, AiReceipts.Purpose.VOICE_REPLY, "/agents/transcribe",
                    "A voice note from the patient (" + Math.max(1, audio.length / 1024) + " KB). It is turned into text and not kept.");
            try {
                transcript = agents.transcribe(audio, "voice.ogg", language);
            } catch (RuntimeException e) {
                log.warn("Voice note not transcribed: {}", e.getClass().getSimpleName());
            }
        }
        if (transcript == null || transcript.isBlank()) {
            drafts.deleteById(patient.getId());
            bot.sendMessage(chatId, PatientMessages.voiceUnheardText(language), REMOVE_KEYBOARD);
            return;
        }
        String text = transcript.strip();
        drafts.save(new VoiceDraft(patient.getId(), text, messageId, clock.instant()));
        bot.sendMessage(chatId, PatientMessages.voiceHeardText(language, text), SEND_KEYBOARD);
    }

    /** The patient tapped Send: their transcript becomes a reply, exactly as if they had typed it. */
    @Transactional
    public boolean confirmVoice(Patient patient, long chatId) {
        VoiceDraft draft = drafts.findById(patient.getId()).orElse(null);
        if (draft == null || clock.instant().isAfter(draft.getCreatedAt().plus(DRAFT_LIFETIME))) {
            if (draft != null) {
                drafts.delete(draft);
            }
            return false;
        }
        drafts.delete(draft);
        UUID clientMessageId = UUID.nameUUIDFromBytes(
                ("telegram-voice:" + chatId + ":" + draft.getSourceMessageId()).getBytes(StandardCharsets.UTF_8));
        followUp.receiveReply(patient, draft.getText(), clientMessageId);
        return true;
    }

    /** Typing anything else replaces a voice transcript that was not sent. */
    @Transactional
    public void discardVoice(Patient patient) {
        drafts.findById(patient.getId()).ifPresent(drafts::delete);
    }

    /**
     * A photo arrived. Like the web app's consent box, nothing is sent to the AI service until the patient taps
     * Read label; until then only Telegram's file id is kept, never the image.
     */
    @Transactional
    public void photo(Patient patient, long chatId, String fileId) {
        photoDrafts.save(new PhotoDraft(patient.getId(), fileId, clock.instant()));
        bot.sendMessage(chatId, PatientMessages.photoConsentText(patient.getPreferredLanguage()), READ_KEYBOARD);
    }

    /** The patient agreed: the photo is downloaded, checked to be an image, read, and what was read is listed. */
    @Transactional
    public boolean confirmPhoto(Patient patient, long chatId) {
        PhotoDraft draft = photoDrafts.findById(patient.getId()).orElse(null);
        if (draft == null) {
            return false;
        }
        photoDrafts.delete(draft);
        if (clock.instant().isAfter(draft.getCreatedAt().plus(DRAFT_LIFETIME))) {
            return false;
        }
        String language = patient.getPreferredLanguage();
        byte[] image = bot.downloadFile(draft.getFileId());
        String mimeType = image == null ? null : MedicationPacketController.detectedImageType(image);
        Map<String, Object> result = null;
        if (mimeType != null) {
            aiReceipts.media(patient, AiReceipts.Purpose.PACKET_PHOTO, "/agents/packet/read",
                    "A photo of a medicine packet sent on Telegram (" + Math.max(1, image.length / 1024) + " KB). The AI reads the printed label; the photo is not kept.");
            try {
                result = agents.readMedicinePacket(image, mimeType);
            } catch (RuntimeException e) {
                log.warn("Packet photo not read: {}", e.getClass().getSimpleName());
            }
        }
        List<String> added = new ArrayList<>();
        if (result != null && result.get("candidates") instanceof List<?> candidates) {
            for (Object c : candidates) {
                if (!(c instanceof Map<?, ?> candidate)) {
                    continue;
                }
                String name = nameOf(candidate);
                if (name == null) {
                    continue;
                }
                MedicationItem.Kind kind = "HERB".equals(candidate.get("kind")) ? MedicationItem.Kind.HERB : MedicationItem.Kind.MEDICINE;
                UUID addedBy = patient.getAccount() != null ? patient.getAccount().getId() : patient.getId();
                medications.save(new MedicationItem(patient, name, kind, PHOTO_SOURCE, Role.PATIENT, addedBy, clock.instant()));
                added.add(name);
            }
        }
        if (added.isEmpty()) {
            bot.sendMessage(chatId, PatientMessages.photoUnreadText(language), REMOVE_KEYBOARD);
            return true;
        }
        graphSync.changed(patient.getId());
        bot.sendMessage(chatId, PatientMessages.photoReadText(language, String.join(", ", added)), REMOVE_KEYBOARD);
        return true;
    }

    /**
     * The patient tapped Check my meal for the photo they sent: the food groups are named and the clinic's own diet
     * tips for them are sent back. Same consent step and the same "not kept" rule as a packet photo.
     */
    @Transactional
    public boolean confirmMeal(Patient patient, long chatId) {
        PhotoDraft draft = photoDrafts.findById(patient.getId()).orElse(null);
        if (draft == null) {
            return false;
        }
        photoDrafts.delete(draft);
        if (clock.instant().isAfter(draft.getCreatedAt().plus(DRAFT_LIFETIME))) {
            return false;
        }
        byte[] image = bot.downloadFile(draft.getFileId());
        bot.sendMessage(chatId, mealAdvice.read(patient, image, "on Telegram").message(), REMOVE_KEYBOARD);
        return true;
    }

    private static String nameOf(Map<?, ?> candidate) {
        String brand = text(candidate.get("brand"));
        String generic = text(candidate.get("generic_candidate"));
        String ingredient = text(candidate.get("ingredient_text"));
        String strength = text(candidate.get("strength"));
        String base = brand != null ? brand : generic != null ? generic : ingredient;
        if (base == null) {
            return null;
        }
        String name = strength == null ? base : base + " " + strength;
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().strip();
        return s.isEmpty() ? null : s;
    }
}
