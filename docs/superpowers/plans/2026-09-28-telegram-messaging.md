# Telegram Messaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace WhatsApp with a Telegram bot for every patient message (summary, check-ins, safety advice, approved answers, acknowledgements) and for inbound patient replies.

**Architecture:** Patients link a Telegram chat by sharing their own, Telegram-verified phone number with the bot; the chat ID is stored encrypted on the patient with a keyed-hash index. All outbound messages keep flowing through `PatientMessages.send`, now addressed by chat ID through a `TelegramMessenger`; inbound updates arrive at a secret-authenticated webhook that links chats or hands text to the existing `FollowUpService.receiveReply`.

**Tech Stack:** Spring Boot 3.3 (Java 21), JPA + Flyway, JUnit 5 + MockMvc + Mockito, Next.js 16 / React 19 / TypeScript, Node 24 for the setup script.

**Spec:** `docs/superpowers/specs/2026-09-28-telegram-messaging-design.md`

## Global Constraints

- Settings: `KHABAR_TELEGRAM_BOT_TOKEN` (secret), `KHABAR_TELEGRAM_WEBHOOK_SECRET` (secret) on `khabar-api`; `NEXT_PUBLIC_TELEGRAM_BOT_USERNAME` (not secret) on `khabar-landing`.
- With no bot token, messages go to the existing outbox (`OutboxMessenger`); local runs and tests do not change.
- The chat ID is stored encrypted (`EncryptedStringConverter`) with a keyed-HMAC index, like the phone.
- Webhook header `X-Telegram-Bot-Api-Secret-Token`, compared in constant time; wrong or missing → 401; otherwise always 200.
- Linking requires `contact.user_id == from.id`.
- No change to message wording in any existing language, triage, the check-in schedule, or caregivers.
- The bot token must never be logged, stored in `outbound_message.error`, or printed.
- Commits carry only the owner's name: `git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit ...` (no co-author trailer).
- API test command (Git Bash): `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q -Dtest=<Classes> test`.

## Review Focus

1. A long visit summary (over Telegram's 4096-character limit) must still arrive, split between lines, not be rejected. → Task 2 test `splitsLongTextBetweenLines`.
2. A network failure must not copy the bot token (it is part of the request URL) into the stored error. → Task 2 test `anUnreachableServerDoesNotLeakTheTokenIntoTheError`.
3. A phone number registered for two patients (a family sharing one phone) must not link the wrong person. → Task 3 test `aNumberRegisteredForTwoPatientsLinksNeither`.
4. The bot added to a group chat must ignore messages there. → Task 3 test `groupChatsAreIgnored`.
5. Telegram's `+60…` phone form must match the clinic's `01x-xxx xxxx` form. → Task 3 test `ownContactLinksAndSendsTheLatestSummary` shares `+60…`.

---

### Task 1: Store a patient's Telegram chat (encrypted + indexed)

**Files:**
- Create: `services/api/src/main/resources/db/migration/V7__patient_telegram_chat.sql`
- Create: `services/api/src/main/java/com/khabar/api/patients/TelegramChatIndex.java`
- Modify: `services/api/src/main/java/com/khabar/api/patients/Patient.java` (table indexes, two fields, three methods)
- Modify: `services/api/src/main/java/com/khabar/api/patients/PatientRepository.java`
- Modify: `services/api/src/test/java/com/khabar/api/config/FlywayMigrationTest.java:31` and add a V7 column check
- Test: `services/api/src/test/java/com/khabar/api/patients/TelegramLinkTest.java`

**Interfaces:**
- Produces: `TelegramChatIndex.of(String chatId) -> String` (null for null/blank); `Patient.linkTelegram(String chatId, String chatIndex)`, `Patient.unlinkTelegram()`, `Patient.getTelegramChatId() -> String`; `PatientRepository.findFirstByTelegramChatIndex(String) -> Optional<Patient>`, `findByTelegramChatIndex(String) -> List<Patient>`, `findByPhoneIndex(String) -> List<Patient>`.

- [ ] **Step 1: Write the failing test** `TelegramLinkTest.java`

```java
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q -Dtest=TelegramLinkTest test`
Expected: compilation failure (`TelegramChatIndex`, `linkTelegram`, `findFirstByTelegramChatIndex` not found).

- [ ] **Step 3: Add the migration** `V7__patient_telegram_chat.sql`

```sql
-- Telegram replaces WhatsApp: the patient's Telegram chat, encrypted, and a keyed hash to find it.
alter table patient add column telegram_chat_enc varchar(512);
alter table patient add column telegram_chat_index varchar(64);
create index idx_patient_telegram_chat_index on patient (telegram_chat_index);
```

- [ ] **Step 4: Add `TelegramChatIndex.java`**

```java
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
```

- [ ] **Step 5: Extend `Patient.java`**

Replace the `@Table` line:

```java
@Table(name = "patient", indexes = {@Index(columnList = "phone_index"), @Index(columnList = "telegram_chat_index")})
```

After the `phoneIndex` field, add:

```java
    /** The patient's Telegram chat, set when they share their own number with the bot. Stored encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "telegram_chat_enc", length = 512)
    private String telegramChatId;

    /** Keyed hash of the chat ID, so an incoming Telegram message can find the patient. */
    @Column(name = "telegram_chat_index", length = 64)
    private String telegramChatIndex;
```

After `setPhoneIndex`, add:

```java
    public void linkTelegram(String chatId, String chatIndex) {
        this.telegramChatId = chatId;
        this.telegramChatIndex = chatIndex;
    }

    public void unlinkTelegram() {
        this.telegramChatId = null;
        this.telegramChatIndex = null;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }
```

Also change the `phoneIndex` field comment to: `/** Keyed hash of the normalised phone, so a number shared with the Telegram bot can find the patient. */`

- [ ] **Step 6: Extend `PatientRepository.java`**

Add (with `import java.util.List;` already present):

```java
    Optional<Patient> findFirstByTelegramChatIndex(String telegramChatIndex);

    List<Patient> findByTelegramChatIndex(String telegramChatIndex);

    List<Patient> findByPhoneIndex(String phoneIndex);
```

- [ ] **Step 7: Update `FlywayMigrationTest`**

Change `assertEquals(6, flyway.migrate().migrationsExecuted);` to `assertEquals(7, flyway.migrate().migrationsExecuted);` and, after the V6 column block, add:

```java
        try (Connection connection = dataSource.getConnection();
             var columns = connection.getMetaData().getColumns(null, null, "PATIENT", "TELEGRAM_CHAT_INDEX")) {
            assertTrue(columns.next(), "V7 must add the Telegram chat index used to find a patient.");
            assertEquals(DatabaseMetaData.columnNullable, columns.getInt("NULLABLE"));
        }
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q -Dtest=TelegramLinkTest,FlywayMigrationTest,PilotSchemaSmokeTest test`
Expected: PASS (no output from `-q`, exit 0). `PilotSchemaSmokeTest` proves Hibernate `validate` accepts V7.

- [ ] **Step 9: Commit**

```bash
git add services/api/src/main/resources/db/migration/V7__patient_telegram_chat.sql services/api/src/main/java/com/khabar/api/patients/TelegramChatIndex.java services/api/src/main/java/com/khabar/api/patients/Patient.java services/api/src/main/java/com/khabar/api/patients/PatientRepository.java services/api/src/test/java/com/khabar/api/patients/TelegramLinkTest.java services/api/src/test/java/com/khabar/api/config/FlywayMigrationTest.java
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Store a patient's Telegram chat encrypted, with a keyed index (V7)"
```

---

### Task 2: Send patient messages through Telegram

**Files:**
- Create: `services/api/src/main/java/com/khabar/api/messaging/TelegramBotClient.java`
- Create: `services/api/src/main/java/com/khabar/api/messaging/TelegramMessenger.java`
- Modify: `services/api/src/main/java/com/khabar/api/messaging/Messenger.java` (doc + parameter name)
- Modify: `services/api/src/main/java/com/khabar/api/messaging/OutboxMessenger.java` (doc + parameter name)
- Modify: `services/api/src/main/java/com/khabar/api/messaging/MessagingConfig.java`
- Modify: `services/api/src/main/java/com/khabar/api/messaging/PatientMessages.java:104`
- Modify: `services/api/src/main/resources/application.yml:60-70`, `application-pilot.yml:35-42`
- Delete: `services/api/src/main/java/com/khabar/api/messaging/WhatsAppCloudMessenger.java`, `services/api/src/test/java/com/khabar/api/messaging/WhatsAppCloudMessengerTest.java`
- Test: `services/api/src/test/java/com/khabar/api/messaging/TelegramBotClientTest.java`, `TelegramMessengerTest.java`

**Interfaces:**
- Consumes: `Patient.getTelegramChatId()` (Task 1).
- Produces: `TelegramBotClient(String baseUrl, String botToken)`, `boolean configured()`, `Messenger.Result sendMessage(long chatId, String text)`, `Messenger.Result sendMessage(long chatId, String text, Map<String, Object> replyMarkup)`; `TelegramMessenger(TelegramBotClient)` with `channel() == "telegram"`; Spring beans `TelegramBotClient` and `Messenger`.

- [ ] **Step 1: Write the failing tests**

`TelegramBotClientTest.java`:

```java
package com.khabar.api.messaging;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Against a stand-in for api.telegram.org, to check exactly what goes on the wire. */
class TelegramBotClientTest {

    static final String TOKEN = "123456:ABC-secret-token";

    HttpServer server;
    final AtomicReference<String> path = new AtomicReference<>();
    final List<String> bodies = new CopyOnWriteArrayList<>();
    int replyStatus = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = (replyStatus == 200 ? "{\"ok\":true,\"result\":{\"message_id\":42}}"
                    : "{\"ok\":false,\"description\":\"Bad Request: chat not found\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(replyStatus, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    TelegramBotClient client() {
        return new TelegramBotClient("http://127.0.0.1:" + server.getAddress().getPort(), TOKEN);
    }

    @Test
    void sendsTheChatAndTextToSendMessage() {
        Messenger.Result result = client().sendMessage(987654321L, "Apa khabar?");
        assertThat(result.delivered()).isTrue();
        assertThat(result.providerId()).isEqualTo("42");
        assertThat(path.get()).isEqualTo("/bot" + TOKEN + "/sendMessage");
        assertThat(bodies.get(0)).contains("\"chat_id\":987654321").contains("\"text\":\"Apa khabar?\"");
        assertThat(bodies.get(0)).doesNotContain("reply_markup");
    }

    @Test
    void sendsTheReplyMarkupWhenGiven() {
        client().sendMessage(1L, "Share?", Map.of("remove_keyboard", true));
        assertThat(bodies.get(0)).contains("\"reply_markup\":{\"remove_keyboard\":true}");
    }

    @Test
    void reportsTelegramsRefusal() {
        replyStatus = 400;
        Messenger.Result result = client().sendMessage(1L, "Hi");
        assertThat(result.delivered()).isFalse();
        assertThat(result.error()).contains("HTTP 400").contains("chat not found");
    }

    @Test
    void anUnreachableServerDoesNotLeakTheTokenIntoTheError() {
        TelegramBotClient client = client();
        server.stop(0);
        Messenger.Result result = client.sendMessage(1L, "Hi");
        assertThat(result.delivered()).isFalse();
        assertThat(result.error()).startsWith("Telegram not reachable").doesNotContain(TOKEN);
    }

    @Test
    void withoutATokenNothingIsSent() {
        TelegramBotClient client = new TelegramBotClient("http://127.0.0.1:" + server.getAddress().getPort(), "");
        assertThat(client.configured()).isFalse();
        assertThat(client.sendMessage(1L, "Hi").error()).isEqualTo("Telegram not configured");
        assertThat(bodies).isEmpty();
    }

    @Test
    void splitsLongTextBetweenLines() {
        String line = "• Metformin 500 mg: 1 tablet, morning and night, after food. ".repeat(20); // ~1240 chars
        String text = String.join("\n", line, line, line, line, line); // ~6200 chars, over 4096
        client().sendMessage(1L, text, Map.of("remove_keyboard", true));
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(0)).doesNotContain("reply_markup");
        assertThat(bodies.get(1)).contains("reply_markup");
        assertThat(TelegramBotClient.parts(text)).allMatch(p -> p.length() <= TelegramBotClient.MAX_TEXT);
        assertThat(String.join("\n", TelegramBotClient.parts(text))).isEqualTo(text);
    }

    @Test
    void aSingleLineLongerThanTheLimitIsCutIntoPieces() {
        String longLine = "x".repeat(TelegramBotClient.MAX_TEXT + 10);
        assertThat(TelegramBotClient.parts(longLine)).containsExactly("x".repeat(TelegramBotClient.MAX_TEXT), "x".repeat(10));
    }
}
```

`TelegramMessengerTest.java`:

```java
package com.khabar.api.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TelegramMessengerTest {

    final TelegramBotClient bot = mock(TelegramBotClient.class);
    final TelegramMessenger messenger = new TelegramMessenger(bot);

    @Test
    void aPatientWithoutALinkedChatIsNotSentAnything() {
        Messenger.Result result = messenger.send(null, "Your care plan", "ms", Messenger.Kind.SUMMARY);
        assertThat(result.delivered()).isFalse();
        assertThat(result.error()).isEqualTo("Telegram not linked");
        verify(bot, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void aLinkedChatIsSentTheText() {
        when(bot.sendMessage(987654321L, "Your care plan")).thenReturn(Messenger.Result.ok("42"));
        assertThat(messenger.send("987654321", "Your care plan", "ms", Messenger.Kind.SUMMARY).delivered()).isTrue();
    }

    @Test
    void theChannelIsTelegram() {
        assertThat(messenger.channel()).isEqualTo("telegram");
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q -Dtest=TelegramBotClientTest,TelegramMessengerTest test`
Expected: compilation failure (`TelegramBotClient`, `TelegramMessenger` not found).

- [ ] **Step 3: Add `TelegramBotClient.java`**

```java
package com.khabar.api.messaging;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Telegram Bot API's sendMessage. Never throws: failures come back in the Result. The bot token is
 * part of the request URL, so errors never include exception messages that could echo it.
 */
public class TelegramBotClient {

    /** Telegram rejects a message longer than this. */
    static final int MAX_TEXT = 4096;

    private final RestClient http;
    private final String sendPath;

    public TelegramBotClient(String baseUrl, String botToken) {
        if (botToken == null || botToken.isBlank()) {
            this.http = null;
            this.sendPath = null;
            return;
        }
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.sendPath = "/bot" + botToken.trim() + "/sendMessage";
    }

    public boolean configured() {
        return http != null;
    }

    public Messenger.Result sendMessage(long chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    /** Long text goes in parts split between lines; the reply markup rides on the last part. */
    public Messenger.Result sendMessage(long chatId, String text, Map<String, Object> replyMarkup) {
        if (http == null) {
            return Messenger.Result.failed("Telegram not configured");
        }
        List<String> parts = parts(text == null ? "" : text);
        Messenger.Result last = Messenger.Result.failed("Empty message");
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId);
            body.put("text", parts.get(i));
            if (replyMarkup != null && i == parts.size() - 1) {
                body.put("reply_markup", replyMarkup);
            }
            last = post(body);
            if (!last.delivered()) {
                return last;
            }
        }
        return last;
    }

    static List<String> parts(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            if (line.length() > MAX_TEXT) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                while (line.length() > MAX_TEXT) {
                    parts.add(line.substring(0, MAX_TEXT));
                    line = line.substring(MAX_TEXT);
                }
            }
            if (current.length() > 0 && current.length() + 1 + line.length() > MAX_TEXT) {
                parts.add(current.toString());
                current.setLength(0);
            } else if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    @SuppressWarnings("unchecked")
    private Messenger.Result post(Map<String, Object> body) {
        try {
            Map<String, Object> reply = http.post().uri(sendPath).body(body).retrieve().body(Map.class);
            Object result = reply == null ? null : reply.get("result");
            Object id = result instanceof Map<?, ?> m ? m.get("message_id") : null;
            return Messenger.Result.ok(id == null ? null : String.valueOf(id));
        } catch (RestClientResponseException e) {
            return Messenger.Result.failed("Telegram answered HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            return Messenger.Result.failed("Telegram not reachable (" + e.getClass().getSimpleName() + ")");
        }
    }
}
```

- [ ] **Step 4: Add `TelegramMessenger.java`**

```java
package com.khabar.api.messaging;

/** Sends patient messages through the Telegram bot, to the chat the patient linked by sharing their number. */
public class TelegramMessenger implements Messenger {

    private final TelegramBotClient bot;

    public TelegramMessenger(TelegramBotClient bot) {
        this.bot = bot;
    }

    @Override
    public Result send(String recipient, String text, String language, Kind kind) {
        if (recipient == null || recipient.isBlank()) {
            return Result.failed("Telegram not linked");
        }
        long chatId;
        try {
            chatId = Long.parseLong(recipient.trim());
        } catch (NumberFormatException e) {
            return Result.failed("Telegram chat ID is not a number");
        }
        return bot.sendMessage(chatId, text);
    }

    @Override
    public String channel() {
        return "telegram";
    }
}
```

- [ ] **Step 5: Update `Messenger.java` and `OutboxMessenger.java`**

In `Messenger.java`, replace the class comment with `/** Sends a message to a patient. Telegram in production; an outbox table locally. */` and the method with:

```java
    /**
     * Never throws: a failure is reported in the result so a follow-up run can carry on.
     * recipient is the patient's linked Telegram chat ID, or null if they have not linked one.
     */
    Result send(String recipient, String text, String language, Kind kind);
```

In `OutboxMessenger.java`, change the comment's first line to `Used when Telegram is not configured (local runs, tests): nothing leaves the machine; every` and rename the parameter `toPhone` to `recipient`.

- [ ] **Step 6: Replace `MessagingConfig.messenger`**

Replace the whole `messenger` bean method with:

```java
    @Bean
    public TelegramBotClient telegramBotClient(@Value("${khabar.telegram.bot-token:}") String botToken,
                                               @Value("${khabar.telegram.api-base-url:https://api.telegram.org}") String baseUrl) {
        return new TelegramBotClient(baseUrl, botToken);
    }

    /** Telegram when a bot token is set; otherwise the local outbox. */
    @Bean
    public Messenger messenger(TelegramBotClient bot) {
        if (!bot.configured()) {
            log.info("Telegram not configured: messages go to the outbox table only.");
            return new OutboxMessenger();
        }
        return new TelegramMessenger(bot);
    }
```

Remove imports that become unused (`@Value` stays).

- [ ] **Step 7: Address by chat in `PatientMessages.send`**

Replace line 104:

```java
        Messenger.Result result = messenger.send(patient.getTelegramChatId(), text, patient.getPreferredLanguage(), kind);
```

- [ ] **Step 8: Replace the WhatsApp sender settings**

In `application.yml`, replace the `whatsapp:` lines for `phone-number-id` through `summary-template` (keep `verify-token` and `app-secret` for Task 3 to remove) so the block reads:

```yaml
  telegram:
    # From @BotFather. Unset: messages are only recorded in the outbound_message table.
    bot-token: ${KHABAR_TELEGRAM_BOT_TOKEN:}
    api-base-url: ${KHABAR_TELEGRAM_API_BASE_URL:https://api.telegram.org}
  whatsapp:
    # Webhook: the token you type into Meta's dashboard, and the app secret that signs deliveries
    verify-token: ${WHATSAPP_VERIFY_TOKEN:}
    app-secret: ${WHATSAPP_APP_SECRET:}
```

In `application-pilot.yml`, replace the `whatsapp:` block with:

```yaml
  telegram:
    bot-token: ${KHABAR_TELEGRAM_BOT_TOKEN:}
  whatsapp:
    verify-token: ${WHATSAPP_VERIFY_TOKEN:}
    app-secret: ${WHATSAPP_APP_SECRET:}
```

- [ ] **Step 9: Delete the WhatsApp sender**

```bash
git rm services/api/src/main/java/com/khabar/api/messaging/WhatsAppCloudMessenger.java services/api/src/test/java/com/khabar/api/messaging/WhatsAppCloudMessengerTest.java
```

`WhatsAppWebhookController` still compiles (it does not use the sender).

- [ ] **Step 10: Run the full API suite**

Run: `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q test`
Expected: PASS. Existing messaging tests use the outbox (no token in the test profile), and `FollowUpCaseTest` still sees the "Patient messages" integration as `LIMITED`.

- [ ] **Step 11: Commit**

```bash
git add -A services/api/src/main/java/com/khabar/api/messaging services/api/src/test/java/com/khabar/api/messaging services/api/src/main/resources/application.yml services/api/src/main/resources/application-pilot.yml
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Send patient messages through a Telegram bot instead of WhatsApp"
```

---

### Task 3: Telegram webhook: link by shared number, replies into follow-up

**Files:**
- Create: `services/api/src/main/java/com/khabar/api/messaging/TelegramWebhookController.java`
- Modify: `services/api/src/main/java/com/khabar/api/messaging/PatientMessages.java` (two localised texts)
- Modify: `services/api/src/main/resources/application.yml`, `application-demo.yml:38-40`, `application-local.yml:34-37`, `application-pilot.yml`, `services/api/src/test/resources/application-test.yml:21-23`
- Modify: `services/api/src/main/java/com/khabar/api/patients/PhoneIndex.java` (comments only)
- Delete: `services/api/src/main/java/com/khabar/api/messaging/WhatsAppWebhookController.java`, `services/api/src/test/java/com/khabar/api/messaging/WhatsAppWebhookTest.java`
- Test: `services/api/src/test/java/com/khabar/api/messaging/TelegramWebhookTest.java`

**Interfaces:**
- Consumes: Task 1 (`TelegramChatIndex.of`, `Patient.linkTelegram/unlinkTelegram/getTelegramChatId`, `PatientRepository.findFirstByTelegramChatIndex/findByTelegramChatIndex/findByPhoneIndex`), Task 2 (`TelegramBotClient.sendMessage(long, String, Map)`), existing `FollowUpService.receiveReply(Patient, String, UUID)`, `PatientMessages.send(Patient, String, Messenger.Kind)`, `VisitSummaryRepository.findFirstByPatientIdOrderByCreatedAtDesc(UUID)`, `PhoneIndex.of(String)`.
- Produces: `POST /api/webhooks/telegram`; `PatientMessages.telegramLinkedText(String language)`, `PatientMessages.textOnlyText(String language)`; constants `TelegramWebhookController.START`, `NOT_OWN`, `NOT_FOUND`, `AMBIGUOUS`, `REMOVE_KEYBOARD`.

- [ ] **Step 1: Write the failing test** `TelegramWebhookTest.java`

```java
package com.khabar.api.messaging;

import com.khabar.api.encounters.VisitSummary;
import com.khabar.api.encounters.VisitSummaryRepository;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.TelegramChatIndex;
import com.khabar.api.service.AgentClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Telegram's webhook: secret-authenticated updates that link a chat by shared number, then carry replies. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TelegramWebhookTest {

    static final String SECRET = "test-telegram-secret";

    @Autowired MockMvc mvc;
    @Autowired ClinicRepository clinics;
    @Autowired PatientRepository patients;
    @Autowired PatientReplyRepository replies;
    @Autowired OutboundMessageRepository outbox;
    @Autowired VisitSummaryRepository summaries;
    @Autowired TelegramChatIndex chatIndex;
    @MockBean AgentClientService agents;
    @MockBean TelegramBotClient bot;

    Clinic clinic;
    Patient aminah;
    long chat;

    @BeforeEach
    void setUp() {
        clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        aminah = patients.save(new Patient(clinic, null, "Aminah binti Yusof", "590312-10-5566", uniqueLocalPhone(), "ms"));
        chat = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "watch", "matched", "pening"));
        when(bot.sendMessage(anyLong(), anyString(), any())).thenReturn(Messenger.Result.ok("1"));
    }

    static String uniqueLocalPhone() {
        return "01" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
    }

    /** Telegram's form of a clinic-typed number: +60 instead of the leading 0. */
    static String telegramForm(String local) {
        return "+6" + local.replaceAll("\\D", "");
    }

    static String message(long chatId, long fromId, String chatType, int messageId, String fields) {
        return """
                {"update_id":%d,"message":{"message_id":%d,"from":{"id":%d,"is_bot":false,"first_name":"A"},
                "chat":{"id":%d,"type":"%s"},"date":1,%s}}
                """.formatted(messageId, messageId, fromId, chatId, chatType, fields);
    }

    String text(int messageId, String text) {
        return message(chat, chat, "private", messageId, "\"text\":\"" + text + "\"");
    }

    String contact(long fromId, long contactUserId, String phone) {
        return message(chat, fromId, "private", 7, "\"contact\":{\"phone_number\":\"%s\",\"first_name\":\"A\",\"user_id\":%d}"
                .formatted(phone, contactUserId));
    }

    ResultActions deliver(String body, String secret) throws Exception {
        var request = post("/api/webhooks/telegram").contentType(MediaType.APPLICATION_JSON).content(body);
        if (secret != null) {
            request.header("X-Telegram-Bot-Api-Secret-Token", secret);
        }
        return mvc.perform(request);
    }

    void link(Patient p) {
        p.linkTelegram(String.valueOf(chat), chatIndex.of(String.valueOf(chat)));
        patients.save(p);
    }

    List<OutboundMessage> sentTo(Patient p, Messenger.Kind kind) {
        return outbox.findAll().stream().filter(m -> m.getPatientId().equals(p.getId()) && m.getKind().equals(kind.name())).toList();
    }

    @Test
    void aWrongOrMissingSecretIsRefused() throws Exception {
        deliver(text(1, "Pening"), "wrong").andExpect(status().isUnauthorized());
        deliver(text(1, "Pening"), null).andExpect(status().isUnauthorized());
        verify(bot, never()).sendMessage(anyLong(), anyString(), any());
    }

    @Test
    void startShowsTheShareNumberButton() throws Exception {
        deliver(text(1, "/start"), SECRET).andExpect(status().isOk());
        verify(bot).sendMessage(eq(chat), eq(TelegramWebhookController.START),
                argThat(markup -> markup != null && markup.toString().contains("request_contact=true")));
    }

    @Test
    void ownContactLinksAndSendsTheLatestSummary() throws Exception {
        summaries.save(new VisitSummary(aminah, UUID.randomUUID(), "ms", "• Metformin 500 mg: 1 biji, pagi dan malam.", null, Instant.now()));

        deliver(contact(chat, chat, telegramForm(aminah.getPhone())), SECRET).andExpect(status().isOk());

        Patient linked = patients.findById(aminah.getId()).orElseThrow();
        assertThat(linked.getTelegramChatId()).isEqualTo(String.valueOf(chat));
        assertThat(patients.findFirstByTelegramChatIndex(chatIndex.of(String.valueOf(chat)))).get()
                .extracting(Patient::getId).isEqualTo(aminah.getId());
        verify(bot).sendMessage(chat, PatientMessages.telegramLinkedText("ms"), TelegramWebhookController.REMOVE_KEYBOARD);
        assertThat(sentTo(aminah, Messenger.Kind.SUMMARY)).extracting(OutboundMessage::getText)
                .containsExactly("• Metformin 500 mg: 1 biji, pagi dan malam.");
    }

    @Test
    void someoneElsesContactIsRefused() throws Exception {
        deliver(contact(chat, chat + 1, telegramForm(aminah.getPhone())), SECRET).andExpect(status().isOk());
        assertThat(patients.findById(aminah.getId()).orElseThrow().getTelegramChatId()).isNull();
        verify(bot).sendMessage(eq(chat), eq(TelegramWebhookController.NOT_OWN), any());
    }

    @Test
    void anUnknownNumberIsNotLinked() throws Exception {
        deliver(contact(chat, chat, "+60199999999"), SECRET).andExpect(status().isOk());
        verify(bot).sendMessage(chat, TelegramWebhookController.NOT_FOUND, TelegramWebhookController.REMOVE_KEYBOARD);
    }

    @Test
    void aNumberRegisteredForTwoPatientsLinksNeither() throws Exception {
        Patient sibling = patients.save(new Patient(clinic, null, "Siti binti Yusof", "600101-10-1111", aminah.getPhone(), "ms"));
        deliver(contact(chat, chat, telegramForm(aminah.getPhone())), SECRET).andExpect(status().isOk());
        assertThat(patients.findById(aminah.getId()).orElseThrow().getTelegramChatId()).isNull();
        assertThat(patients.findById(sibling.getId()).orElseThrow().getTelegramChatId()).isNull();
        verify(bot).sendMessage(chat, TelegramWebhookController.AMBIGUOUS, TelegramWebhookController.REMOVE_KEYBOARD);
    }

    @Test
    void relinkingAChatToAnotherPatientUnlinksTheFirst() throws Exception {
        Patient other = patients.save(new Patient(clinic, null, "Tan Kok Hoe", "540101-07-1234", uniqueLocalPhone(), "zh"));
        link(other);
        deliver(contact(chat, chat, telegramForm(aminah.getPhone())), SECRET).andExpect(status().isOk());
        assertThat(patients.findById(other.getId()).orElseThrow().getTelegramChatId()).isNull();
        assertThat(patients.findById(aminah.getId()).orElseThrow().getTelegramChatId()).isEqualTo(String.valueOf(chat));
    }

    @Test
    void aLinkedPatientsTextBecomesATriagedReply() throws Exception {
        link(aminah);
        deliver(text(11, "Pening dan berpeluh"), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).anyMatch(r -> r.getPatient().getId().equals(aminah.getId())
                && r.getLevel() == TriageLevel.WATCH && r.getText().equals("Pening dan berpeluh"));
        assertThat(sentTo(aminah, Messenger.Kind.NOTICE)).hasSize(1);
    }

    @Test
    void theSameUpdateDeliveredTwiceIsTriagedOnce() throws Exception {
        link(aminah);
        deliver(text(12, "Pening"), SECRET).andExpect(status().isOk());
        deliver(text(12, "Pening"), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).filteredOn(r -> r.getPatient().getId().equals(aminah.getId())).hasSize(1);
        verify(agents, times(1)).triageReply(anyString(), any());
        assertThat(sentTo(aminah, Messenger.Kind.NOTICE)).hasSize(1);
    }

    @Test
    void aPhotoFromALinkedPatientGetsTheTextOnlyPrompt() throws Exception {
        link(aminah);
        deliver(message(chat, chat, "private", 13, "\"photo\":[{\"file_id\":\"x\",\"width\":1,\"height\":1}]"), SECRET)
                .andExpect(status().isOk());
        assertThat(replies.findAll()).noneMatch(r -> r.getPatient().getId().equals(aminah.getId()));
        assertThat(sentTo(aminah, Messenger.Kind.NOTICE)).extracting(OutboundMessage::getText)
                .containsExactly(PatientMessages.textOnlyText("ms"));
    }

    @Test
    void aStartCommandFromALinkedPatientIsNotAReply() throws Exception {
        link(aminah);
        deliver(text(14, "/start"), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).noneMatch(r -> r.getPatient().getId().equals(aminah.getId()));
        assertThat(sentTo(aminah, Messenger.Kind.NOTICE)).extracting(OutboundMessage::getText)
                .containsExactly(PatientMessages.telegramLinkedText("ms"));
    }

    @Test
    void anUnlinkedChatsTextGetsTheShareNumberPrompt() throws Exception {
        deliver(text(15, "Pening"), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).noneMatch(r -> r.getText().equals("Pening") && r.getPatient().getId().equals(aminah.getId()));
        verify(bot).sendMessage(eq(chat), eq(TelegramWebhookController.START), any());
    }

    @Test
    void groupChatsAreIgnored() throws Exception {
        link(aminah);
        deliver(message(chat, chat, "group", 16, "\"text\":\"Pening\""), SECRET).andExpect(status().isOk());
        assertThat(replies.findAll()).noneMatch(r -> r.getPatient().getId().equals(aminah.getId()));
        verify(bot, never()).sendMessage(anyLong(), anyString(), any());
    }

    @Test
    void updatesWithoutAMessageAreAcknowledgedAndIgnored() throws Exception {
        deliver("{\"update_id\":99,\"edited_message\":{\"message_id\":1}}", SECRET).andExpect(status().isOk());
        verify(bot, never()).sendMessage(anyLong(), anyString(), any());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q -Dtest=TelegramWebhookTest test`
Expected: compilation failure (`TelegramWebhookController`, `telegramLinkedText`, `textOnlyText` not found).

- [ ] **Step 3: Add the localised texts to `PatientMessages.java`**

Next to the other text maps, add:

```java
    /** Sent once a patient links Telegram. zh and ta need fluent-reader review like the rest of this file. */
    private static final Map<String, String> TELEGRAM_LINKED = Map.of(
            "ms", "Telegram anda kini disambungkan ke Khabar. Klinik anda akan menghantar pelan rawatan dan semakan susulan di sini.",
            "en", "Your Telegram is now connected to Khabar. Your clinic will send your care plan and check-ins here.",
            "zh", "您的 Telegram 已连接到 Khabar。诊所会在这里发送您的护理计划和随访问候。",
            "ta", "உங்கள் Telegram இப்போது Khabar உடன் இணைக்கப்பட்டுள்ளது. உங்கள் மருத்துவமனை பராமரிப்புத் திட்டத்தையும் பின்தொடர் செய்திகளையும் இங்கே அனுப்பும்.");

    private static final Map<String, String> TEXT_ONLY = Map.of(
            "ms", "Sila balas dalam bentuk teks.",
            "en", "Please reply in text.",
            "zh", "请用文字回复。",
            "ta", "தயவுசெய்து உரையாகப் பதிலளிக்கவும்.");

    public static String telegramLinkedText(String language) {
        return TELEGRAM_LINKED.getOrDefault(language, TELEGRAM_LINKED.get("en"));
    }

    public static String textOnlyText(String language) {
        return TEXT_ONLY.getOrDefault(language, TEXT_ONLY.get("en"));
    }
```

- [ ] **Step 4: Add `TelegramWebhookController.java`**

```java
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
```

- [ ] **Step 5: Replace the WhatsApp webhook settings**

`application.yml`: in the `telegram:` block add `webhook-secret: ${KHABAR_TELEGRAM_WEBHOOK_SECRET:}` (with the comment `# Random secret given to setWebhook; Telegram sends it back on every delivery.`) and delete the remaining `whatsapp:` block.
`application-pilot.yml`: add `webhook-secret: ${KHABAR_TELEGRAM_WEBHOOK_SECRET:}` under `telegram:` and delete `whatsapp:`.
`application-demo.yml`: replace the `whatsapp:` block with:

```yaml
  telegram:
    webhook-secret: ${KHABAR_TELEGRAM_WEBHOOK_SECRET:}
```

`application-local.yml`: replace the `whatsapp:` block with:

```yaml
  telegram:
    # Lets you try the webhook locally with curl (see services/README.md); not used by real Telegram
    webhook-secret: local-telegram-secret
```

`application-test.yml`: replace the `whatsapp:` block with:

```yaml
  telegram:
    webhook-secret: test-telegram-secret
```

- [ ] **Step 6: Delete the WhatsApp webhook and fix comments**

```bash
git rm services/api/src/main/java/com/khabar/api/messaging/WhatsAppWebhookController.java services/api/src/test/java/com/khabar/api/messaging/WhatsAppWebhookTest.java
```

In `PhoneIndex.java`, change "lets an incoming WhatsApp message find its patient" to "lets a number shared with the Telegram bot find its patient", and the `normalise` comment to `/** Malaysian numbers as digits only, country code 60 instead of the leading 0 (Telegram's form without the +). */`.

Replace the remaining WhatsApp wording (comments and one test name only; no behaviour changes):

| File | Line | New text |
| --- | --- | --- |
| `patients/Patient.java` | 51 | `/** Keyed hash of the normalised phone, so a number shared with the Telegram bot can find the patient. */` |
| `dev/DemoData.java` | 53 | `numbers use 03-0000 xxxx, which no real line has, so no real Telegram account can share a number that links to a demo patient` (keep the rest of the sentence's grammar) |
| `dev/FakePatientGenerator.java` | 14 | `line can have, so no real Telegram account can ever link to a generated patient. The same seed always gives` |
| `dev/DevMessagingController.java` | 18 | ``/** `local` profile only: send due check-ins now, and see what would have gone out on Telegram. */`` |
| `followup/FollowUpController.java` | 19 | `/** A follow-up reply sent from the Khabar app. Telegram replies arrive through the webhook instead. */` |
| `followup/FollowUpService.java` | 25 | `* A patient's reply, from the app or Telegram: triaged (identity removed first), stored encrypted,` |
| `messaging/OutboundMessage.java` | 31 | `/** telegram or outbox */` |
| test `dev/FakePatientGeneratorTest.java` | 51 | `// 03-0000 xxxx is not a routable Malaysian number, so no real Telegram account can link to it` (keep the rest of the line) |
| test `messaging/PhoneIndexTest.java` | 31 | rename `normalisesToWhatsAppFormat` to `normalisesToTelegramFormat` |

(`ClinicOpsController` is changed in Task 4.)

- [ ] **Step 7: Run the webhook test, then the full API suite**

Run: `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q -Dtest=TelegramWebhookTest test`
Expected: PASS.
Run: `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q test`
Expected: PASS; `grep -rni "whatsapp" services/api/src` prints only `ClinicOpsController.java` (fixed in Task 4).

- [ ] **Step 8: Commit**

```bash
git add -A services/api/src
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Receive patient replies and link chats through a Telegram webhook"
```

---

### Task 4: Show Telegram status to patients and admins

**Files:**
- Modify: `services/api/src/main/java/com/khabar/api/identity/MeController.java`
- Modify: `services/api/src/main/java/com/khabar/api/clinicops/ClinicOpsController.java:165-170`
- Create: `web/components/home/telegram-card.tsx`
- Modify: `web/lib/types.ts:5`, `web/components/home/patient-home.tsx` (after the `quickRow` section), `web/app/home/home.module.css`
- Test: `services/api/src/test/java/com/khabar/api/identity/MeTelegramTest.java`

**Interfaces:**
- Consumes: `Patient.getTelegramChatId()` (Task 1); `Messenger.channel()` (`"telegram"` from Task 2).
- Produces: `/api/me` field `telegramLinked: boolean`; web `Me.telegramLinked?: boolean`; `TelegramCard({ linked }: { linked: boolean })`.

- [ ] **Step 1: Write the failing test** `MeTelegramTest.java`

```java
package com.khabar.api.identity;

import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.TelegramChatIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeTelegramTest {

    @Autowired MockMvc mvc;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired TelegramChatIndex chatIndex;

    Patient patientWithAccount(String name) {
        Clinic clinic = clinics.save(new Clinic("Klinik Me"));
        AppUser account = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, name, null));
        return patients.save(new Patient(clinic, account, name, "590312-10-5566", "012-000 0001", "ms"));
    }

    @Test
    void aPatientWhoLinkedTelegramSeesItAsLinked() throws Exception {
        Patient p = patientWithAccount("Aminah");
        p.linkTelegram("123", chatIndex.of("123"));
        patients.save(p);
        mvc.perform(get("/api/me").header("Authorization", bearer(p.getAccount().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramLinked").value(true));
    }

    @Test
    void aPatientWhoHasNotLinkedSeesItAsNotLinked() throws Exception {
        Patient p = patientWithAccount("Rosnah");
        mvc.perform(get("/api/me").header("Authorization", bearer(p.getAccount().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramLinked").value(false));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q -Dtest=MeTelegramTest test`
Expected: FAIL (`No value at JSON path "$.telegramLinked"`).

- [ ] **Step 3: Add `telegramLinked` to `MeController`**

Change the record to end with `List<ClinicStaffRole> clinicRoles, boolean telegramLinked`, and in `me(...)` replace the `patientId` computation with:

```java
        Patient own = user.getRole() == Role.PATIENT ? patients.findByAccountId(user.getId()).orElse(null) : null;
        UUID patientId = own == null ? null : own.getId();
        boolean telegramLinked = own != null && own.getTelegramChatId() != null;
```

and pass `telegramLinked` as the last constructor argument.

- [ ] **Step 4: Report Telegram in the admin integration row (`ClinicOpsController`)**

Replace the `boolean whatsapp = ...` line and the "Patient messages" entry with:

```java
        boolean telegram = "telegram".equalsIgnoreCase(messenger.channel());
```

```java
                new Integration("Patient messages", telegram ? "OK" : "LIMITED",
                        telegram ? "Sent through Telegram to patients who linked the Khabar bot." : "Kept in the local outbox; nothing reaches patients' phones."),
```

- [ ] **Step 5: Run the API tests**

Run: `cd services/api && export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1" && ./mvnw -q -Dtest=MeTelegramTest,OnboardingTest,FollowUpCaseTest test`
Expected: PASS; `grep -rni "whatsapp" services/api/src` now prints nothing.

- [ ] **Step 6: Add the web card**

`web/lib/types.ts` line 5: add `telegramLinked?: boolean` inside the `Me` type.

`web/components/home/telegram-card.tsx`:

```tsx
"use client";

import { CheckCircle2, Send } from "lucide-react";
import styles from "@/app/home/home.module.css";

/** Points the patient to the clinic's Telegram bot; linking happens in Telegram by sharing their own number. */
export function TelegramCard({ linked }: { linked: boolean }) {
  const bot = process.env.NEXT_PUBLIC_TELEGRAM_BOT_USERNAME?.replace(/^@/, "");
  if (!bot) return null;
  if (linked) {
    return (
      <section className={styles.telegramCard} aria-label="Telegram">
        <CheckCircle2 size={19} />
        <div>
          <strong>Telegram connected</strong>
          <small>Your care plan and check-ins arrive in Telegram.</small>
        </div>
      </section>
    );
  }
  return (
    <section className={styles.telegramCard} aria-label="Telegram">
      <Send size={19} />
      <div>
        <strong>Get your care plan on Telegram</strong>
        <small>Open the Khabar bot, then tap “Share my phone number”.</small>
      </div>
      <a className="button-secondary" href={`https://t.me/${bot}`} target="_blank" rel="noopener noreferrer">
        Open Telegram
      </a>
    </section>
  );
}
```

`web/app/home/home.module.css` (append):

```css
.telegramCard {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--paper);
}
.telegramCard > div { flex: 1; display: grid; gap: 2px; }
.telegramCard small { color: var(--ink-soft); }
@media (max-width: 560px) { .telegramCard { flex-wrap: wrap; } }
```

`web/components/home/patient-home.tsx`: add `import { TelegramCard } from "./telegram-card";` and, directly after the closing `</section>` of `<section className={styles.quickRow}>`, add `<TelegramCard linked={Boolean(me.telegramLinked)} />`.

- [ ] **Step 7: Check the web app**

Run: `cd web && npm run lint && npm run typecheck && npm run build`
Expected: all pass.
Browser check against the local stack (`services/api` with the `local` profile, `NEXT_PUBLIC_KHABAR_API_URL=http://localhost:8080`): with `NEXT_PUBLIC_TELEGRAM_BOT_USERNAME` unset, the demo patient home shows no Telegram card; with `NEXT_PUBLIC_TELEGRAM_BOT_USERNAME=khabar_demo_bot`, it shows "Get your care plan on Telegram" with a link to `https://t.me/khabar_demo_bot`.

- [ ] **Step 8: Commit**

```bash
git add services/api/src/main/java/com/khabar/api/identity/MeController.java services/api/src/main/java/com/khabar/api/clinicops/ClinicOpsController.java services/api/src/test/java/com/khabar/api/identity/MeTelegramTest.java web/lib/types.ts web/components/home/telegram-card.tsx web/components/home/patient-home.tsx web/app/home/home.module.css
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Show patients and admins whether Telegram is connected"
```

---

### Task 5: One-off webhook registration script

**Files:**
- Create: `services/scripts/telegram-set-webhook.mjs`
- Test: run against a local stub (below)

**Interfaces:**
- Consumes: the owner's bot token and webhook secret (typed, hidden); `POST https://api.telegram.org/bot<token>/setWebhook`.
- Produces: a registered webhook at `https://khabar-api.vercel.app/api/webhooks/telegram`.

- [ ] **Step 1: Write the script**

```js
#!/usr/bin/env node
// Registers Khabar's Telegram webhook once. Asks for the bot token and webhook secret with hidden input
// and never prints them. The secret must equal KHABAR_TELEGRAM_WEBHOOK_SECRET on khabar-api.
//
// Usage: node scripts/telegram-set-webhook.mjs [apiBaseUrl] [telegramApiBase]
const apiBaseUrl = process.argv[2] ?? "https://khabar-api.vercel.app";
const telegram = process.argv[3] ?? "https://api.telegram.org";

function hidden(question) {
  return new Promise((resolve) => {
    process.stdout.write(question);
    const stdin = process.stdin;
    let value = "";
    const onData = (chunk) => {
      for (const ch of chunk) {
        if (ch === "\r" || ch === "\n") {
          stdin.setRawMode?.(false);
          stdin.pause();
          stdin.off("data", onData);
          process.stdout.write("\n");
          resolve(value.trim());
          return;
        }
        if (ch === "\u0003") process.exit(1);
        if (ch === "\u007f" || ch === "\b") value = value.slice(0, -1);
        else value += ch;
      }
    };
    stdin.setRawMode?.(true);
    stdin.setEncoding("utf8");
    stdin.resume();
    stdin.on("data", onData);
  });
}

const token = await hidden("Telegram bot token (hidden): ");
const secret = await hidden("Webhook secret, same as KHABAR_TELEGRAM_WEBHOOK_SECRET (hidden): ");
if (!token || !secret) {
  console.error("Both values are needed.");
  process.exit(1);
}
if (!/^[A-Za-z0-9_-]{1,256}$/.test(secret)) {
  console.error("Telegram only accepts letters, digits, _ and - in the secret (1-256 characters).");
  process.exit(1);
}

const url = new URL("/api/webhooks/telegram", apiBaseUrl).toString();
const call = async (method, body) => {
  const response = await fetch(`${telegram}/bot${token}/${method}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body ?? {}),
  });
  return response.json().catch(() => ({ ok: false, description: `HTTP ${response.status}` }));
};

const set = await call("setWebhook", { url, secret_token: secret, allowed_updates: ["message"], drop_pending_updates: true });
console.log(set.ok ? `Webhook set: ${url}` : `Telegram refused: ${set.description}`);
const info = await call("getWebhookInfo");
if (info.ok) {
  console.log(`Telegram reports: url=${info.result.url} pending=${info.result.pending_update_count}` +
    (info.result.last_error_message ? ` last error=${info.result.last_error_message}` : ""));
}
if (!set.ok) process.exitCode = 1;
```

- [ ] **Step 2: Test it against a stub**

Create `C:/Users/tanho/AppData/Local/Temp/tg-stub.mjs` (outside the repo):

```js
import http from "node:http";
http.createServer((req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", () => {
    console.error(`STUB ${req.url} ${body}`);
    res.writeHead(200, { "content-type": "application/json" });
    res.end(req.url.endsWith("/getWebhookInfo")
      ? JSON.stringify({ ok: true, result: { url: "https://example.test/api/webhooks/telegram", pending_update_count: 0 } })
      : JSON.stringify({ ok: true, result: true }));
  });
}).listen(9310);
```

Run: `node C:/Users/tanho/AppData/Local/Temp/tg-stub.mjs & sleep 1; printf 'TESTTOKEN\nsecret_abc-123\n' | node services/scripts/telegram-set-webhook.mjs https://example.test http://127.0.0.1:9310; kill %1`
Expected: the stub logs `/botTESTTOKEN/setWebhook` with `"url":"https://example.test/api/webhooks/telegram","secret_token":"secret_abc-123"`; the script prints `Webhook set: https://example.test/api/webhooks/telegram` and never prints `TESTTOKEN` or the secret.

- [ ] **Step 3: Commit**

```bash
git add services/scripts/telegram-set-webhook.mjs
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Add a one-off script to register the Telegram webhook"
```

---

### Task 6: Documentation

**Files:**
- Modify: `README.md`, `docs/DECISIONS.md`, `docs/SETUP_INTEGRATIONS.md`, `UNDONE_WORK.md`, `docs/PITCH_SCRIPTS.md`, `services/README.md`

- [ ] **Step 1: List every WhatsApp mention outside history records**

Run: `grep -n -i "whatsapp" README.md docs/DECISIONS.md docs/SETUP_INTEGRATIONS.md UNDONE_WORK.md docs/PITCH_SCRIPTS.md services/README.md`
Dated run records (`docs/DEMO_RUN_*`, `docs/BUG_BASH_*`, `docs/TEST_RESULTS.md`) stay as history and are not edited.

- [ ] **Step 2: Rewrite each mention for Telegram**

- README: solution table ("sent on Telegram"), idea C rationale, comparison table rows, tech-stack **Messaging** row, "What's live" follow-up row, and the architecture line naming the channel. Add to the Messaging row's constraints: *"Telegram is less used than WhatsApp in Malaysia, especially by older adults. Chosen because a bot needs no business approval or message templates, has no test-recipient limit, and patients link by sharing their own number, which doubles as consent. Bot chats are not end-to-end encrypted; fictional data only."*
- `docs/DECISIONS.md`: add a row dated 28 Sep: *Telegram replaces WhatsApp for patient messages* | *Meta template approval and the five-recipient test limit blocked a live loop; a Telegram bot works the same day* | *Rejected: waiting for WhatsApp templates; running both channels.* Note the risk (lower Telegram use among target patients).
- `docs/SETUP_INTEGRATIONS.md`: replace the WhatsApp section with **Telegram**: (1) @BotFather `/newbot`, keep the token; (2) make a secret with `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`; (3) in `services/`: `npx vercel env add KHABAR_TELEGRAM_BOT_TOKEN production` and `npx vercel env add KHABAR_TELEGRAM_WEBHOOK_SECRET production` (both Secret); in `web/`: `npx vercel env add NEXT_PUBLIC_TELEGRAM_BOT_USERNAME production` (Config, the bot's username); (4) deploy the API from an up-to-date `main`; (5) `node scripts/telegram-set-webhook.mjs`; (6) linking needs the patient's registered phone to be the number on their Telegram account.
- `UNDONE_WORK.md` §2.1: retitle to *Telegram bot* and replace the tasks with: create the bot and settings; register the webhook; link a fictional patient registered with the tester's own number; receive the summary on linking; send a reply and see it on the call list; confirm a Telegram retry is not triaged twice. Update the owner-action "Providers" line.
- `docs/PITCH_SCRIPTS.md`: replace WhatsApp with Telegram and add one honest line on why.
- `services/README.md`: replace the WhatsApp webhook curl example with a Telegram one:

```bash
curl -s -X POST localhost:8080/api/webhooks/telegram -H "Content-Type: application/json" \
  -H "X-Telegram-Bot-Api-Secret-Token: local-telegram-secret" \
  -d '{"update_id":1,"message":{"message_id":1,"from":{"id":5},"chat":{"id":5,"type":"private"},"text":"/start"}}'
```

- [ ] **Step 3: Check nothing stale remains**

Run: `grep -n -i "whatsapp" README.md docs/DECISIONS.md docs/SETUP_INTEGRATIONS.md UNDONE_WORK.md docs/PITCH_SCRIPTS.md services/README.md`
Expected: only the deliberate mentions (the decision row, "replaces WhatsApp", the comparison with WhatsApp's reach).

- [ ] **Step 4: Commit**

```bash
git add README.md docs/DECISIONS.md docs/SETUP_INTEGRATIONS.md UNDONE_WORK.md docs/PITCH_SCRIPTS.md services/README.md
git -c user.name="tanhs" -c user.email="tanhongsheng050204@gmail.com" commit -m "Document Telegram as the patient messaging channel"
```

---

### Task 7: Pull request, deployment and live check (owner steps included)

- [ ] **Step 1: Push and open the PR; wait for CI** (API tests including PostgreSQL 16 migration V1–V7, agents, web).
- [ ] **Step 2 (owner):** create the bot with @BotFather; set the three settings (Task 6 setup steps) **before** merging, so the web deploy picks up `NEXT_PUBLIC_TELEGRAM_BOT_USERNAME`.
- [ ] **Step 3 (owner):** merge; then pull `main` locally, confirm the Telegram files are present, and run `npx vercel deploy --prod` from `services/`; `node scripts/check-health.mjs`.
- [ ] **Step 4 (owner):** `node scripts/telegram-set-webhook.mjs`; expect `Webhook set` and `pending=0`.
- [ ] **Step 5 (owner):** as the doctor, register a fictional patient with the tester's own Telegram phone number and finalise a short visit; open the bot, `/start`, share the number; expect the linked message and the summary in Telegram; reply "sakit dada"; expect the 999 wording in Telegram and the reply at the top of the doctor's call list. Read `npx vercel logs khabar-api.vercel.app --since 30m` right away if anything fails.
- [ ] **Step 6:** record the result in `docs/DEMO_RUN_CHECKLIST.md` and `UNDONE_WORK.md` §2.1.
