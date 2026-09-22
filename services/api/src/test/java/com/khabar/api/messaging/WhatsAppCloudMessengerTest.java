package com.khabar.api.messaging;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Against a stand-in for graph.facebook.com, to check exactly what goes on the wire. */
class WhatsAppCloudMessengerTest {

    HttpServer server;
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<String> auth = new AtomicReference<>();
    final AtomicReference<String> body = new AtomicReference<>();
    final List<String> bodies = new java.util.concurrent.CopyOnWriteArrayList<>();
    int replyStatus = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            bodies.add(body.get());
            byte[] reply = (replyStatus == 200 ? "{\"messages\":[{\"id\":\"wamid.ABC\"}]}" : "{\"error\":{\"message\":\"bad\"}}").getBytes(StandardCharsets.UTF_8);
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

    WhatsAppCloudMessenger messenger(String template) {
        return messenger(template, "");
    }

    WhatsAppCloudMessenger messenger(String checkInTemplate, String summaryTemplate) {
        return new WhatsAppCloudMessenger("http://127.0.0.1:" + server.getAddress().getPort(), "v21.0", "12345", "token-xyz",
                checkInTemplate, summaryTemplate);
    }

    @Test
    void sendsATextMessageToTheNormalisedNumber() {
        Messenger.Result result = messenger("").send("012-345 6789", "Apa khabar?", "ms", Messenger.Kind.SUMMARY);

        assertThat(result.delivered()).isTrue();
        assertThat(result.providerId()).isEqualTo("wamid.ABC");
        assertThat(path.get()).isEqualTo("/v21.0/12345/messages");
        assertThat(auth.get()).isEqualTo("Bearer token-xyz");
        assertThat(body.get()).contains("\"to\":\"60123456789\"").contains("\"type\":\"text\"").contains("Apa khabar?");
    }

    @Test
    void checkInsUseTheApprovedTemplateInThePatientsLanguage() {
        messenger("khabar_checkin").send("012-345 6789", "ignored", "zh", Messenger.Kind.CHECK_IN);
        assertThat(body.get()).contains("\"type\":\"template\"").contains("\"name\":\"khabar_checkin\"").contains("\"code\":\"zh_CN\"");
    }

    @Test
    void anErrorFromMetaIsReportedNotThrown() {
        replyStatus = 400;
        Messenger.Result result = messenger("").send("012-345 6789", "x", "en", Messenger.Kind.SUMMARY);
        assertThat(result.delivered()).isFalse();
        assertThat(result.error()).contains("400");
    }

    @Test
    void summariesUseTheSummaryTemplateWithTheTextAsItsParameterOnOneLine() {
        Messenger.Result result = messenger("", "khabar_summary")
                .send("012-345 6789", "• Metformin 500 mg: 1 biji\n• Amlodipine 5 mg: 1 biji", "ms", Messenger.Kind.SUMMARY);

        assertThat(result.delivered()).isTrue();
        assertThat(body.get()).contains("\"type\":\"template\"").contains("\"name\":\"khabar_summary\"").contains("\"code\":\"ms\"")
                .contains("\"type\":\"body\"").contains("• Metformin 500 mg: 1 biji | • Amlodipine 5 mg: 1 biji")
                .doesNotContain("\\n");
    }

    @Test
    void aLongSummaryIsSplitAtLineBreaksSoNoMedicineIsCut() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            text.append("• Medicine number ").append(i).append(" 500 mg: 1 tablet in the morning and at night, after food, for two weeks.\n");
        }

        messenger("", "khabar_summary").send("012-345 6789", text.toString(), "en", Messenger.Kind.SUMMARY);

        assertThat(bodies).hasSizeGreaterThan(1);
        String all = String.join("", bodies);
        for (int i = 1; i <= 12; i++) {
            assertThat(all).contains("Medicine number " + i + " 500 mg: 1 tablet in the morning and at night, after food, for two weeks.");
        }
    }

    @Test
    void otherMessagesAreStillPlainTextBecauseThePatientHasJustWritten() {
        messenger("khabar_checkin", "khabar_summary").send("012-345 6789", "Terima kasih.", "ms", Messenger.Kind.NOTICE);
        assertThat(body.get()).contains("\"type\":\"text\"");
    }
}
