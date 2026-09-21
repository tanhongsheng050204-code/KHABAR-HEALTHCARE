package com.khabar.api.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

    private static final String KEY = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private final FieldEncryptor encryptor = new FieldEncryptor(KEY);

    @Test
    void decryptsWhatItEncrypted() {
        String stored = encryptor.encrypt("590312-10-5566");
        assertThat(encryptor.decrypt(stored)).isEqualTo("590312-10-5566");
    }

    @Test
    void storedValueDoesNotContainThePlainText() {
        assertThat(encryptor.encrypt("590312-10-5566")).doesNotContain("590312");
    }

    @Test
    void encryptingTheSameValueTwiceGivesDifferentCiphertext() {
        assertThat(encryptor.encrypt("012-345 6789")).isNotEqualTo(encryptor.encrypt("012-345 6789"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String stored = encryptor.encrypt("012-345 6789");
        int mid = stored.length() / 2;
        char flipped = stored.charAt(mid) == 'A' ? 'B' : 'A';
        String tampered = stored.substring(0, mid) + flipped + stored.substring(mid + 1);
        assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullStaysNull() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void refusesAMissingKey() {
        assertThatThrownBy(() -> new FieldEncryptor("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAKeyThatIsNot256BitHex() {
        assertThatThrownBy(() -> new FieldEncryptor("0123456789abcdef")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesTheWellKnownPlaceholderKey() {
        assertThatThrownBy(() -> new FieldEncryptor("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
