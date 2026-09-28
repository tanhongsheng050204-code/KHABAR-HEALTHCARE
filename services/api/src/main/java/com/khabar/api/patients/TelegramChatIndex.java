package com.khabar.api.patients;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A blind index for Telegram chat IDs, like PhoneIndex. The chat ID is stored encrypted with a random
 * IV, so this keyed hash is what lets an incoming Telegram message find its patient.
 */
@Component
public class TelegramChatIndex {

    private final byte[] key;

    public TelegramChatIndex(@Value("${khabar.security.field-encryption-key:}") String fieldEncryptionKey) {
        try {
            this.key = MessageDigest.getInstance("SHA-256")
                    .digest(("khabar-telegram-chat-index:" + fieldEncryptionKey).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String of(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(chatId.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
