package com.khabar.api.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Talks to a real (tiny) HTTP server, because the bug this guards against only shows up on the wire. */
class AgentClientServiceTest {

    HttpServer server;
    final AtomicReference<String> upgradeHeader = new AtomicReference<>();
    final AtomicReference<String> serviceKey = new AtomicReference<>();
    final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void startFakeAgentsService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/agents/intake/chat", exchange -> {
            upgradeHeader.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            serviceKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = "{\"next_question\":\"Sejak bila?\",\"is_complete\":false}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsPlainHttp11WithTheBodyBecauseUvicornRejectsH2cUpgrades() {
        AgentClientService client = new AgentClientService("http://127.0.0.1:" + server.getAddress().getPort(), "secret-key");

        Map<String, Object> reply = client.processIntake("graph-1", "ms", List.of(Map.of("role", "user", "content", "pening")));

        assertThat(upgradeHeader.get()).isNull();
        assertThat(serviceKey.get()).isEqualTo("secret-key");
        assertThat(body.get()).contains("\"graph_id\":\"graph-1\"").contains("pening");
        assertThat(reply).containsEntry("next_question", "Sejak bila?");
    }
}
