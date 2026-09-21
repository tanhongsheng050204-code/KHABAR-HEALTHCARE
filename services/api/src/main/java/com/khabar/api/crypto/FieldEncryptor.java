package com.khabar.api.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * AES-256-GCM encryption for sensitive columns (IC number, phone, doctor's notes).
 * Stored format: base64(12-byte IV || ciphertext || 16-byte auth tag). GCM's tag
 * means any change to the stored value is detected on decrypt.
 * Only this service holds the key; it comes from the FIELD_ENCRYPTION_KEY environment variable.
 */
public class FieldEncryptor {

    private static final Pattern HEX_256 = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final String PLACEHOLDER_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(String hexKey) {
        if (hexKey == null || !HEX_256.matcher(hexKey).matches()) {
            throw new IllegalArgumentException("FIELD_ENCRYPTION_KEY must be 64 hex characters (256 bits). Generate one with: openssl rand -hex 32");
        }
        if (hexKey.equalsIgnoreCase(PLACEHOLDER_KEY)) {
            throw new IllegalArgumentException("FIELD_ENCRYPTION_KEY is still the example placeholder. Generate a real key with: openssl rand -hex 32");
        }
        this.key = new SecretKeySpec(HexFormat.of().parseHex(hexKey), "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt field", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Stored field could not be decrypted: wrong key or tampered value", e);
        }
    }
}
