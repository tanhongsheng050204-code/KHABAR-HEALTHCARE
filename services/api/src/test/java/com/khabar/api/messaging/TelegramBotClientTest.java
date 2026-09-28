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
        String line = "• Metformin 500 mg: 1 tablet, morning and night, after food. ".repeat(20);
        String text = String.join("\n", line, line, line, line, line);
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
