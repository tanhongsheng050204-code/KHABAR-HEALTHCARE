package com.khabar.api.messaging;

import com.khabar.api.patients.PhoneIndex;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends through Meta's WhatsApp Cloud API. Check-ins and visit summaries start the conversation, so
 * outside a 24-hour reply window WhatsApp only allows them as pre-approved templates: set the names in
 * khabar.whatsapp.checkin-template and summary-template (one version per language). The summary
 * template has one body parameter, {{1}}, which carries the summary. Answers and notices go as plain
 * text, because the patient has just written to us.
 */
public class WhatsAppCloudMessenger implements Messenger {

    /** Khabar language -> WhatsApp template language code. */
    private static final Map<String, String> TEMPLATE_LANGUAGES = Map.of("ms", "ms", "en", "en", "zh", "zh_CN", "ta", "ta");

    private final RestClient http;
    private final String messagesPath;
    private final String checkInTemplate;
    private final String summaryTemplate;

    /** Template parameters may not contain line breaks, and the whole body is capped at 1024 characters. */
    static final int MAX_PARAMETER = 900;

    public WhatsAppCloudMessenger(String baseUrl, String apiVersion, String phoneNumberId, String accessToken,
                                  String checkInTemplate, String summaryTemplate) {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.messagesPath = "/" + apiVersion + "/" + phoneNumberId + "/messages";
        this.checkInTemplate = checkInTemplate == null ? "" : checkInTemplate.trim();
        this.summaryTemplate = summaryTemplate == null ? "" : summaryTemplate.trim();
    }

    @Override
    public Result send(String toPhone, String text, String language, Kind kind) {
        String to = PhoneIndex.normalise(toPhone);
        if (to == null) {
            return Result.failed("No phone number");
        }
        String code = TEMPLATE_LANGUAGES.getOrDefault(language, "en");
        if (kind == Kind.CHECK_IN && !checkInTemplate.isEmpty()) {
            return post(Map.of("messaging_product", "whatsapp", "to", to, "type", "template",
                    "template", Map.of("name", checkInTemplate, "language", Map.of("code", code))));
        }
        if (kind == Kind.SUMMARY && !summaryTemplate.isEmpty()) {
            Result last = Result.failed("Empty summary");
            for (String part : parts(text)) {
                last = post(Map.of("messaging_product", "whatsapp", "to", to, "type", "template",
                        "template", Map.of("name", summaryTemplate, "language", Map.of("code", code),
                                "components", List.of(Map.of("type", "body",
                                        "parameters", List.of(Map.of("type", "text", "text", part)))))));
                if (!last.delivered()) {
                    return last;
                }
            }
            return last;
        }
        return post(Map.of("messaging_product", "whatsapp", "to", to, "type", "text",
                "text", Map.of("preview_url", false, "body", text)));
    }

    /** The summary as template parameters: lines joined with " | ", split only between lines so no medicine is cut. */
    static List<String> parts(String text) {
        List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : text.split("\\R")) {
            String line = raw.replaceAll("\\s+", " ").trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.length() > MAX_PARAMETER) {
                line = line.substring(0, MAX_PARAMETER - 1) + "…";
            }
            if (current.length() > 0 && current.length() + 3 + line.length() > MAX_PARAMETER) {
                parts.add(current.toString());
                current.setLength(0);
            }
            current.append(current.length() > 0 ? " | " : "").append(line);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    @SuppressWarnings("unchecked")
    private Result post(Map<String, Object> body) {
        try {
            Map<String, Object> reply = http.post().uri(messagesPath).body(body).retrieve().body(Map.class);
            List<Map<String, Object>> messages = reply == null ? null : (List<Map<String, Object>>) reply.get("messages");
            String id = messages == null || messages.isEmpty() ? null : String.valueOf(messages.get(0).get("id"));
            return Result.ok(id);
        } catch (RestClientResponseException e) {
            return Result.failed("WhatsApp answered HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            return Result.failed("WhatsApp not reachable: " + e.getMessage());
        }
    }

    @Override
    public String channel() {
        return "whatsapp";
    }
}
