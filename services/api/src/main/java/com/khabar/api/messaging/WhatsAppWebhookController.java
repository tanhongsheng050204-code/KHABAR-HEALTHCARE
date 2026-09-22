package com.khabar.api.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.followup.FollowUpService;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.PhoneIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Meta's WhatsApp webhook. The GET is the one-time subscription handshake; each POST carries
 * patients' messages and is signed with the app secret (X-Hub-Signature-256), which is what
 * authenticates it: there is no user token here.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final String verifyToken;
    private final String appSecret;
    private final ObjectMapper json;
    private final PatientRepository patients;
    private final PhoneIndex phoneIndex;
    private final FollowUpService followUp;

    public WhatsAppWebhookController(@Value("${khabar.whatsapp.verify-token:}") String verifyToken,
                                     @Value("${khabar.whatsapp.app-secret:}") String appSecret,
                                     ObjectMapper json, PatientRepository patients, PhoneIndex phoneIndex, FollowUpService followUp) {
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
        this.json = json;
        this.patients = patients;
        this.phoneIndex = phoneIndex;
        this.followUp = followUp;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String subscribe(@RequestParam("hub.mode") String mode, @RequestParam("hub.verify_token") String token,
                            @RequestParam("hub.challenge") String challenge) {
        if (verifyToken.isBlank() || !"subscribe".equals(mode) || !constantTimeEquals(verifyToken, token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return challenge;
    }

    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@RequestBody String body, @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (appSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "WhatsApp webhook is not configured.");
        }
        if (signature == null || !constantTimeEquals(expectedSignature(body), signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bad signature.");
        }
        try {
            for (JsonNode entry : json.readTree(body).path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    for (JsonNode message : change.path("value").path("messages")) {
                        handle(message);
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not JSON.");
        }
        // Meta retries anything that is not 200, so unknown senders are still acknowledged.
        return "EVENT_RECEIVED";
    }

    private void handle(JsonNode message) {
        if (!"text".equals(message.path("type").asText())) {
            return;
        }
        String from = message.path("from").asText();
        String text = message.path("text").path("body").asText();
        if (text.isBlank()) {
            return;
        }
        patients.findFirstByPhoneIndex(phoneIndex.of(from)).ifPresentOrElse(
                patient -> followUp.receiveReply(patient, text),
                () -> log.info("WhatsApp message from a number not linked to any patient; ignored."));
    }

    private String expectedSignature(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
