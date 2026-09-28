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
