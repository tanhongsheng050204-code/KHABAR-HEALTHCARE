package com.khabar.api.messaging;

import com.khabar.api.patients.PhoneIndex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneIndexTest {

    final PhoneIndex index = new PhoneIndex("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90");

    @Test
    void theSameNumberWrittenDifferentlyGivesTheSameIndex() {
        String expected = index.of("60123456789");
        assertThat(index.of("012-345 6789")).isEqualTo(expected);
        assertThat(index.of("+60 12-345 6789")).isEqualTo(expected);
        assertThat(index.of("0123456789")).isEqualTo(expected);
    }

    @Test
    void differentNumbersGiveDifferentIndexes() {
        assertThat(index.of("012-345 6789")).isNotEqualTo(index.of("012-345 6788"));
    }

    @Test
    void theIndexDoesNotRevealTheNumber() {
        assertThat(index.of("012-345 6789")).doesNotContain("123456789").hasSize(64);
    }

    @Test
    void normalisesToWhatsAppFormat() {
        assertThat(PhoneIndex.normalise("012-345 6789")).isEqualTo("60123456789");
        assertThat(PhoneIndex.normalise("+60 12-345 6789")).isEqualTo("60123456789");
        assertThat(PhoneIndex.normalise(null)).isNull();
    }
}
