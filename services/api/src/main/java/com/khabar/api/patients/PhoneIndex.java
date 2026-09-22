package com.khabar.api.patients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * A "blind index" for phone numbers. Phones are stored encrypted with a random IV, so they cannot
 * be searched; this keyed hash of the normalised number lets an incoming WhatsApp message find its
 * patient without the number ever being stored in the clear.
 */
@Component
public class PhoneIndex {

    private final byte[] key;

    public PhoneIndex(@Value("${khabar.security.field-encryption-key:}") String fieldEncryptionKey) {
        try {
            this.key = MessageDigest.getInstance("SHA-256")
                    .digest(("khabar-phone-index:" + fieldEncryptionKey).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Malaysian numbers in WhatsApp's form: digits only, country code 60 instead of the leading 0. */
    public static String normalise(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        return digits.startsWith("0") ? "6" + digits : digits;
    }

    public String of(String phone) {
        String normalised = normalise(phone);
        if (normalised == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalised.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
