package com.khabar.api.caregivers;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CheckIn;
import com.khabar.api.followup.CheckInRepository;
import com.khabar.api.identity.AppUser;
import com.khabar.api.messaging.Messenger;
import com.khabar.api.messaging.TelegramBotClient;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.TelegramChatIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Telegram alerts for family caregivers. Only caregivers the patient shares alerts with (SUMMARY_AND_ALERTS) get
 * them, and only once they link their own chat. An alert says what happened in one line and never quotes the
 * patient's messages: the patient went quiet, reported missed doses, or sent something that may be urgent.
 */
@Service
public class CaregiverAlerts {

    private static final Logger log = LoggerFactory.getLogger(CaregiverAlerts.class);
    static final Duration LINK_CODE_LIFETIME = Duration.ofMinutes(30);
    static final Duration QUIET_AFTER = Duration.ofHours(48);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String QUIET = "QUIET";
    public static final String MISSED_DOSES = "MISSED_DOSES";
    public static final String URGENT_REPLY = "URGENT_REPLY";

    public static final String LINKED_TEXT = "Khabar: chat ini kini menerima amaran untuk pesakit yang berkongsi amaran dengan anda.\n\n"
            + "Khabar: this chat now gets alerts about the patients who share alerts with you. "
            + "Messages are never quoted here; open Khabar for details.";
    public static final String BAD_CODE_TEXT = "Kod ini tidak sah atau telah tamat tempoh. Buat kod baharu dalam Khabar.\n\n"
            + "That code is not valid or has expired. Make a new one in Khabar.";
    public static final String ALERTS_ONLY = "Chat ini hanya menerima amaran Khabar. Untuk menghubungi klinik, gunakan aplikasi Khabar.\n\n"
            + "This chat only gets Khabar alerts. To reach the clinic, use the Khabar app.";

    public record LinkCode(String code, Instant expiresAt, String command) {
    }

    public record Status(boolean telegramConfigured, boolean linked, Instant linkedAt, boolean codePending,
                         List<NoticeView> recent) {
    }

    public record NoticeView(UUID patientId, String kind, Instant sentAt, boolean delivered) {
    }

    private final CaregiverTelegramRepository chats;
    private final CaregiverNoticeRepository notices;
    private final CaregiverLinkRepository links;
    private final CheckInRepository checkIns;
    private final TelegramChatIndex chatIndex;
    private final TelegramBotClient bot;
    private final AdjustableClock clock;
    private final TransactionTemplate tx;
    @org.springframework.beans.factory.annotation.Autowired
    private com.khabar.api.access.FamilyGroups family;

    public CaregiverAlerts(CaregiverTelegramRepository chats, CaregiverNoticeRepository notices, CaregiverLinkRepository links,
                           CheckInRepository checkIns, TelegramChatIndex chatIndex, TelegramBotClient bot, AdjustableClock clock,
                           TransactionTemplate tx) {
        this.chats = chats;
        this.notices = notices;
        this.links = links;
        this.checkIns = checkIns;
        this.chatIndex = chatIndex;
        this.bot = bot;
        this.clock = clock;
        this.tx = tx;
    }

    // ---- Linking --------------------------------------------------------------------------------------------

    public LinkCode newLinkCode(AppUser caregiver) {
        CaregiverTelegram chat = chats.findById(caregiver.getId()).orElseGet(() -> new CaregiverTelegram(caregiver.getId()));
        String code = "CG-" + randomCode(8);
        Instant expires = clock.instant().plus(LINK_CODE_LIFETIME);
        chat.newLinkCode(hash(code), expires);
        chats.save(chat);
        return new LinkCode(code, expires, "/link " + code);
    }

    /** "/link CODE" from a private chat. True when the code was valid and the chat is now linked. */
    public boolean linkFromTelegram(String code, String chatId) {
        if (code == null || code.isBlank() || chatId == null || chatId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(tx.execute(status -> chats.findByLinkCodeHash(hash(code)).map(chat -> {
            if (chat.getLinkCodeExpiresAt() == null || clock.instant().isAfter(chat.getLinkCodeExpiresAt())) {
                return false;
            }
            String index = chatIndex.of(chatId);
            // One chat belongs to one caregiver account: a re-link moves it.
            chats.findByChatIndex(index).stream()
                    .filter(other -> !other.getCaregiverId().equals(chat.getCaregiverId()))
                    .forEach(other -> {
                        other.unlink();
                        chats.save(other);
                    });
            chat.link(chatId, index, clock.instant());
            chats.save(chat);
            return true;
        }).orElse(false)));
    }

    public boolean isCaregiverChat(String chatId) {
        return !chats.findByChatIndex(chatIndex.of(chatId)).isEmpty();
    }

    public void unlink(AppUser caregiver) {
        chats.findById(caregiver.getId()).ifPresent(chat -> {
            chat.unlink();
            chats.save(chat);
        });
    }

    public Status status(AppUser caregiver) {
        Optional<CaregiverTelegram> chat = chats.findById(caregiver.getId());
        boolean pending = chat.map(c -> c.getLinkCodeExpiresAt() != null && c.getLinkCodeExpiresAt().isAfter(clock.instant())).orElse(false);
        List<NoticeView> recent = notices.findTop20ByCaregiverIdOrderBySentAtDesc(caregiver.getId()).stream()
                .map(n -> new NoticeView(n.getPatientId(), n.getKind(), n.getSentAt(), n.isDelivered()))
                .toList();
        return new Status(bot.configured(), chat.map(c -> c.getChatId() != null).orElse(false),
                chat.map(CaregiverTelegram::getLinkedAt).orElse(null), pending, recent);
    }

    // ---- Alerts ---------------------------------------------------------------------------------------------

    /** Patients who left a check-in unanswered for two days. Each check-in alerts each caregiver once. */
    public void checkQuiet() {
        Instant cutoff = clock.instant().minus(QUIET_AFTER);
        // Only recent silences: a check-in quiet for weeks was already the clinic's business long ago.
        Instant oldest = cutoff.minus(Duration.ofDays(3));
        for (CheckIn checkIn : checkIns.findByStatusAndSentAtBefore(CheckIn.Status.SENT, cutoff)) {
            if (checkIn.getSentAt() != null && checkIn.getSentAt().isAfter(oldest)) {
                notify(checkIn.getPatient(), QUIET, checkIn.getId());
            }
        }
    }

    /**
     * Sends one alert of this kind about this event to each caregiver the patient shares alerts with. Never
     * throws: an alert is a courtesy on top of the clinic's own call list.
     */
    public int notify(Patient patient, String kind, UUID ref) {
        int sent = 0;
        try {
            for (CaregiverLink link : links.findByPatientIdAndRevokedAtIsNull(patient.getId())) {
                if (link.getScope() != CaregiverScope.SUMMARY_AND_ALERTS) {
                    continue;
                }
                UUID caregiverId = link.getCaregiver().getId();
                Optional<CaregiverTelegram> chat = chats.findById(caregiverId).filter(c -> c.getChatId() != null);
                if (chat.isEmpty() || notices.existsByCaregiverIdAndKindAndRef(caregiverId, kind, ref)) {
                    continue;
                }
                Messenger.Result result = bot.sendMessage(Long.parseLong(chat.get().getChatId()), text(kind, patient.getFullName()));
                notices.save(new CaregiverNotice(caregiverId, patient.getId(), kind, ref, clock.instant(), result.delivered()));
                sent++;
            }
            // The patient's own family group, if they linked one, hears the same news once (recorded under the patient's id).
            if (!notices.existsByCaregiverIdAndKindAndRef(patient.getId(), kind, ref)
                    && family.copy(patient.getId(), text(kind, patient.getFullName()), null)) {
                notices.save(new CaregiverNotice(patient.getId(), patient.getId(), kind, ref, clock.instant(), true));
                sent++;
            }
        } catch (RuntimeException e) {
            log.warn("Caregiver alert not sent: {}", e.getClass().getSimpleName());
        }
        return sent;
    }

    static String text(String kind, String name) {
        return switch (kind) {
            case QUIET -> "Khabar: " + name + " belum menjawab semakan klinik selama 2 hari. Mungkin elok bertanya khabar.\n\n"
                    + "Khabar: " + name + " has not answered the clinic's check-in for 2 days. You may want to check on them.";
            case MISSED_DOSES -> "Khabar: " + name + " memberitahu terlepas ubat 2 kali dalam 3 hari. Klinik boleh melihat ini.\n\n"
                    + "Khabar: " + name + " reported missing medicine twice in 3 days. The clinic can see this.";
            case URGENT_REPLY -> "Khabar: " + name + " menghantar mesej kepada klinik yang mungkin kecemasan. Ia berada di atas senarai "
                    + "panggilan klinik. Jika anda bersamanya dan ini kecemasan, hubungi 999.\n\n"
                    + "Khabar: " + name + " sent the clinic a message that may be urgent. It is at the top of the clinic's call "
                    + "list. If you are with them and it is an emergency, call 999.";
            default -> throw new IllegalArgumentException(kind);
        };
    }

    static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("khabar-caregiver-link:" + code.trim().toUpperCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
