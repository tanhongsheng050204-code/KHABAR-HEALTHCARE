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

    /** Photos and voice notes larger than this are not downloaded. */
    static final int MAX_DOWNLOAD = 8 * 1024 * 1024;

    private final RestClient http;
    private final String sendPath;
    private final String botPath;

    public TelegramBotClient(String baseUrl, String botToken) {
        if (botToken == null || botToken.isBlank()) {
            this.http = null;
            this.sendPath = null;
            this.botPath = null;
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
        this.botPath = "/bot" + botToken.trim();
    }

    /**
     * Downloads a file a user sent (a voice note or a photo) through getFile. Returns null when Telegram is not
     * configured, the file is too large, or anything fails; the caller tells the patient to type instead.
     */
    @SuppressWarnings("unchecked")
    public byte[] downloadFile(String fileId) {
        if (http == null || fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> reply = http.get().uri(botPath + "/getFile?file_id={id}", fileId).retrieve().body(Map.class);
            Object result = reply == null ? null : reply.get("result");
            if (!(result instanceof Map<?, ?> file) || file.get("file_path") == null) {
                return null;
            }
            // A file without a stated size is refused, and the download stops at the cap whatever the server sends.
            if (!(file.get("file_size") instanceof Number n) || n.longValue() > MAX_DOWNLOAD) {
                return null;
            }
            return http.get().uri("/file" + botPath + "/" + file.get("file_path")).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    return null;
                }
                try (java.io.InputStream in = response.getBody()) {
                    byte[] bytes = in.readNBytes(MAX_DOWNLOAD + 1);
                    return bytes.length > MAX_DOWNLOAD ? null : bytes;
                }
            });
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Stops the spinner on a pressed button and optionally shows a short notice. Never throws. */
    public void answerCallback(String callbackId, String text) {
        if (http == null || callbackId == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackId);
        if (text != null) {
            body.put("text", text.length() > 190 ? text.substring(0, 190) : text);
        }
        call("/answerCallbackQuery", body);
    }

    /** Removes the buttons from a message once they have been used. Never throws. */
    public void removeButtons(long chatId, long messageId) {
        if (http == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", List.of()));
        call("/editMessageReplyMarkup", body);
    }

    /** Sends an MP3 as a voice message. Never throws; false when it could not be sent. */
    public boolean sendVoice(long chatId, byte[] mp3) {
        if (http == null || mp3 == null || mp3.length == 0) {
            return false;
        }
        try {
            org.springframework.util.LinkedMultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
            form.add("chat_id", Long.toString(chatId));
            form.add("voice", new org.springframework.core.io.ByteArrayResource(mp3) {
                @Override
                public String getFilename() {
                    return "check-in.mp3";
                }
            });
            http.post().uri(botPath + "/sendVoice").contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void call(String method, Map<String, Object> body) {
        try {
            http.post().uri(botPath + method).body(body).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            // Cosmetic calls: the action they follow has already been recorded.
        }
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
