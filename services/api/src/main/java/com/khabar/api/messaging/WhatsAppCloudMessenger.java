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
 * Sends through Meta's WhatsApp Cloud API. Check-ins start the conversation, so outside a 24-hour
 * reply window WhatsApp only allows them as a pre-approved template: set the template name in
 * khabar.whatsapp.checkin-template (one version per language). Everything else goes as text.
 */
public class WhatsAppCloudMessenger implements Messenger {

    /** Khabar language -> WhatsApp template language code. */
    private static final Map<String, String> TEMPLATE_LANGUAGES = Map.of("ms", "ms", "en", "en", "zh", "zh_CN", "ta", "ta");

    private final RestClient http;
    private final String messagesPath;
    private final String checkInTemplate;

    public WhatsAppCloudMessenger(String baseUrl, String apiVersion, String phoneNumberId, String accessToken, String checkInTemplate) {
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
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result send(String toPhone, String text, String language, Kind kind) {
        String to = PhoneIndex.normalise(toPhone);
        if (to == null) {
            return Result.failed("No phone number");
        }
        Map<String, Object> body = kind == Kind.CHECK_IN && !checkInTemplate.isEmpty()
                ? Map.of("messaging_product", "whatsapp", "to", to, "type", "template",
                        "template", Map.of("name", checkInTemplate, "language", Map.of("code", TEMPLATE_LANGUAGES.getOrDefault(language, "en"))))
                : Map.of("messaging_product", "whatsapp", "to", to, "type", "text",
                        "text", Map.of("preview_url", false, "body", text));
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
