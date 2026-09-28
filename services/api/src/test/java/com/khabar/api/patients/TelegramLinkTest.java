package com.khabar.api.patients;

import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** A patient's Telegram chat is stored encrypted and found through its keyed index. */
@SpringBootTest
@ActiveProfiles("test")
class TelegramLinkTest {

    @Autowired ClinicRepository clinics;
    @Autowired PatientRepository patients;
    @Autowired TelegramChatIndex index;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aLinkedChatIsStoredEncryptedAndFoundByItsIndex() {
        Clinic clinic = clinics.save(new Clinic("Klinik Telegram"));
        Patient p = patients.save(new Patient(clinic, null, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        p.linkTelegram("987654321", index.of("987654321"));
        patients.save(p);

        Patient found = patients.findFirstByTelegramChatIndex(index.of("987654321")).orElseThrow();
        assertEquals(p.getId(), found.getId());
        assertEquals("987654321", found.getTelegramChatId());
        String stored = jdbc.queryForObject("select telegram_chat_enc from patient where id = ?", String.class, p.getId());
        assertNotNull(stored);
        assertNotEquals("987654321", stored);
    }

    @Test
    void unlinkingClearsTheChat() {
        Clinic clinic = clinics.save(new Clinic("Klinik Telegram"));
        Patient p = patients.save(new Patient(clinic, null, "Tan Kok Hoe", "540101-07-1234", "016-222 3333", "zh"));
        p.linkTelegram("555", index.of("555"));
        patients.save(p);
        p.unlinkTelegram();
        patients.save(p);
        assertNull(patients.findById(p.getId()).orElseThrow().getTelegramChatId());
        assertTrue(patients.findFirstByTelegramChatIndex(index.of("555")).isEmpty());
    }

    @Test
    void theIndexIsKeyedAndTrimsOnlySurroundingSpace() {
        assertEquals(index.of("123"), index.of(" 123 "));
        assertNotEquals(index.of("123"), index.of("124"));
        assertNotEquals(index.of("123"), new TelegramChatIndex("another-key").of("123"));
        assertNull(index.of(null));
        assertNull(index.of(" "));
    }
}
