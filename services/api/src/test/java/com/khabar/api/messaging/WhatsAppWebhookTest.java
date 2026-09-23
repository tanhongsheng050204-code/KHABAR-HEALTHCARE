package com.khabar.api.messaging;

import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Meta's webhook: a subscription handshake, then signed deliveries of patients' WhatsApp messages. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WhatsAppWebhookTest {

    static final String APP_SECRET = "test-app-secret";
    static final String VERIFY_TOKEN = "test-verify-token";

    @Autowired MockMvc mvc;
    @Autowired ClinicRepository clinics;
    @Autowired PatientRepository patients;
    @Autowired PatientReplyRepository replies;
    @MockBean AgentClientService agents;

    Patient aminah;

    @BeforeEach
    void setUp() {
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        // A number unique to this test run, written the way a clinic would type it
        String local = "01" + (100_000_00 + (int) (Math.random() * 899_999_99));
        aminah = patients.save(new Patient(clinic, null, "Aminah binti Yusof", "590312-10-5566", local, "ms"));
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "watch", "matched", "pening"));
    }

    String whatsAppNumber(Patient p) {
        return "6" + p.getPhone().replaceAll("\\D", "");
    }

    static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    static String delivery(String from, String text) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{"field":"messages","value":{
                "messaging_product":"whatsapp","messages":[{"from":"%s","id":"wamid.1","timestamp":"1","type":"text","text":{"body":"%s"}}]}}]}]}
                """.formatted(from, text);
    }

    ResultActions deliver(String body, String signature) throws Exception {
        return mvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", signature).content(body));
    }

    @Test
    void theSubscriptionHandshakeEchoesTheChallenge() throws Exception {
        mvc.perform(get("/api/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", VERIFY_TOKEN).param("hub.challenge", "1234"))
                .andExpect(status().isOk()).andExpect(content().string("1234"));
    }

    @Test
    void aWrongVerifyTokenIsRefused() throws Exception {
        mvc.perform(get("/api/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "guess").param("hub.challenge", "1234"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSignedMessageFromAKnownPatientBecomesATriagedReply() throws Exception {
        String body = delivery(whatsAppNumber(aminah), "Pening dan berpeluh");
        deliver(body, sign(body)).andExpect(status().isOk());
        assertThat(replies.findAll()).anyMatch(r -> r.getPatient().getId().equals(aminah.getId())
                && r.getLevel() == TriageLevel.WATCH && r.getText().equals("Pening dan berpeluh"));
    }

    @Test
    void anUnsignedOrForgedDeliveryIsRefused() throws Exception {
        String body = delivery(whatsAppNumber(aminah), "Pening");
        deliver(body, "sha256=" + "0".repeat(64)).andExpect(status().isForbidden());
        assertThat(replies.findAll()).noneMatch(r -> r.getPatient().getId().equals(aminah.getId()));
    }

    @Test
    void aMessageFromAnUnknownNumberIsAcceptedButIgnored() throws Exception {
        String body = delivery("60100000000", "Hello");
        deliver(body, sign(body)).andExpect(status().isOk());
    }
}
