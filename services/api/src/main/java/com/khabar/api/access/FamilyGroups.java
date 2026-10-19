package com.khabar.api.access;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.messaging.Messenger;
import com.khabar.api.messaging.TelegramBotClient;
import com.khabar.api.patients.TelegramChatIndex;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The patient's family group on Telegram. The patient makes a code in the app, adds the Khabar bot to the family
 * group and sends "/family CODE" there. From then on the group gets copies of the patient's reminders and the
 * caregiver-style alerts. A message typed in the group goes nowhere: replies to the clinic stay the patient's own.
 */
@Service
public class FamilyGroups {

    static final Duration LINK_CODE_LIFETIME = Duration.ofMinutes(30);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String LINKED = "Khabar: kumpulan keluarga ini kini menerima peringatan ubat dan temu janji %s. "
            + "Mesej yang ditaip di sini tidak dihantar kepada klinik.\n\n"
            + "Khabar: this family group now gets %s's medicine and visit reminders. Messages typed here do not go to the clinic.";
    public static final String BAD_CODE = "Kod keluarga ini tidak sah atau telah tamat tempoh.\n\nThat family code is not valid or has expired.";

    public record LinkCode(String code, Instant expiresAt, String command) {
    }

    public record Status(boolean telegramConfigured, boolean linked, Instant linkedAt) {
    }

    private final FamilyGroupRepository groups;
    private final TelegramChatIndex chatIndex;
    private final TelegramBotClient bot;
    private final AdjustableClock clock;
    private final TransactionTemplate tx;

    public FamilyGroups(FamilyGroupRepository groups, TelegramChatIndex chatIndex, TelegramBotClient bot, AdjustableClock clock,
                        TransactionTemplate tx) {
        this.groups = groups;
        this.chatIndex = chatIndex;
        this.bot = bot;
        this.clock = clock;
        this.tx = tx;
    }

    public LinkCode newLinkCode(UUID patientId) {
        FamilyGroup group = groups.findById(patientId).orElseGet(() -> new FamilyGroup(patientId));
        StringBuilder code = new StringBuilder("FAM-");
        for (int i = 0; i < 8; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        Instant expires = clock.instant().plus(LINK_CODE_LIFETIME);
        group.newLinkCode(hash(code.toString()), expires);
        groups.save(group);
        return new LinkCode(code.toString(), expires, "/family " + code);
    }

    /** "/family CODE" sent in a group. Returns the patient whose group it now is. */
    public Optional<UUID> linkFromTelegram(String code, String chatId) {
        if (code == null || code.isBlank() || chatId == null || chatId.isBlank()) {
            return Optional.empty();
        }
        return tx.execute(status -> groups.findByLinkCodeHash(hash(code)).flatMap(group -> {
            if (group.getLinkCodeExpiresAt() == null || clock.instant().isAfter(group.getLinkCodeExpiresAt())) {
                return Optional.<UUID>empty();
            }
            String index = chatIndex.of(chatId);
            // One group belongs to one patient.
            groups.findByChatIndex(index).stream().filter(g -> !g.getPatientId().equals(group.getPatientId())).forEach(g -> {
                g.unlink();
                groups.save(g);
            });
            group.link(chatId, index, clock.instant());
            groups.save(group);
            return Optional.of(group.getPatientId());
        }));
    }

    /** The patient whose family group this chat is, if any. */
    public Optional<UUID> patientForChat(String chatId) {
        return groups.findByChatIndex(chatIndex.of(chatId)).stream().findFirst().map(FamilyGroup::getPatientId);
    }

    public Status status(UUID patientId) {
        Optional<FamilyGroup> group = groups.findById(patientId);
        return new Status(bot.configured(), group.map(g -> g.getChatId() != null).orElse(false), group.map(FamilyGroup::getLinkedAt).orElse(null));
    }

    public void unlink(UUID patientId) {
        groups.findById(patientId).ifPresent(g -> {
            g.unlink();
            groups.save(g);
        });
    }

    /** A copy for the family group, if the patient linked one. Never throws; false when nothing was sent. */
    public boolean copy(UUID patientId, String text, Map<String, Object> markup) {
        try {
            Optional<FamilyGroup> group = groups.findById(patientId).filter(g -> g.getChatId() != null);
            if (group.isEmpty() || !bot.configured()) {
                return false;
            }
            Messenger.Result result = bot.sendMessage(Long.parseLong(group.get().getChatId()), "👪 " + text, markup);
            return result != null && result.delivered();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("khabar-family-link:" + code.trim().toUpperCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
