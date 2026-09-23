package com.khabar.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** HTTP client for the Python agents service. Every call carries the shared internal service key. */
@Service
public class AgentClientService {

    private final RestClient restClient;

    public AgentClientService(
            @Value("${khabar.services.agents-url}") String agentsBaseUrl,
            @Value("${khabar.security.internal-service-key}") String internalServiceKey) {
        // Force HTTP/1.1: by default the JDK client asks plain-http servers to upgrade to HTTP/2 (h2c),
        // which uvicorn rejects, and the request body is lost.
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(agentsBaseUrl)
                .defaultHeader("X-Internal-Service-Key", internalServiceKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> checkAgentHealth() {
        try {
            return restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    /** Structures the doctor's (already de-identified) notes into a draft report. */
    public AgentDtos.DraftedReport draftReport(String notes) {
        return restClient.post()
                .uri("/agents/report/draft")
                .body(Map.of("notes", notes))
                .retrieve()
                .body(AgentDtos.DraftedReport.class);
    }

    /** Runs the data-based safety checks on a draft report. */
    public AgentDtos.SafetyCheckResult checkSafety(AgentDtos.SafetyDraft draft) {
        return restClient.post()
                .uri("/agents/evaluator/check")
                .body(draft)
                .retrieve()
                .body(AgentDtos.SafetyCheckResult.class);
    }

    /** Builds the patient's plain-language summary from the prescription. */
    public AgentDtos.SummaryResult buildSummary(List<AgentDtos.DraftedRx> prescription, String language, Double followUpWeeks, boolean fasting) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("prescription", prescription);
        body.put("language", language);
        body.put("follow_up_weeks", followUpWeeks);
        body.put("fasting", fasting);
        return restClient.post()
                .uri("/agents/summary/build")
                .body(body)
                .retrieve()
                .body(AgentDtos.SummaryResult.class);
    }

    /**
     * Returns {"level": "red|watch|ok|review", "matched": word or null}. Throws if the agents service is unreachable.
     * graphId lets the triage model read the reply knowing the patient's conditions and medicines; it may be null.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> triageReply(String text, String graphId) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("text", text);
        body.put("graph_id", graphId);
        return restClient.post()
                .uri("/agents/followup/triage")
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    /** context is what the clinic already knows, with no identifiers: medicines, allergies. */
    public Map<String, Object> processIntake(String graphId, String preferredLanguage, List<Map<String, String>> messages,
                                             Map<String, Object> context) {
        Map<String, Object> payload = Map.of(
                "graph_id", graphId,
                "preferred_language", preferredLanguage,
                "messages", messages,
                "context", context == null ? Map.of() : context
        );

        return restClient.post()
                .uri("/agents/intake/chat")
                .body(payload)
                .retrieve()
                .body(Map.class);
    }

    public AgentDtos.PreVisitReport previsitReport(List<Map<String, String>> messages) {
        return restClient.post()
                .uri("/agents/intake/report")
                .body(Map.of("messages", messages))
                .retrieve()
                .body(AgentDtos.PreVisitReport.class);
    }

    /** The id of the approved answer the reply asks for, or null. */
    public String matchAnswer(String text, List<AgentDtos.AnswerOption> options) {
        AgentDtos.AnswerMatch match = restClient.post()
                .uri("/agents/followup/answer")
                .body(Map.of("text", text, "options", options))
                .retrieve()
                .body(AgentDtos.AnswerMatch.class);
        return match == null ? null : match.answerId();
    }

    /** Duplicates, clashes, herbs and allergies within what the patient already takes. */
    public List<AgentDtos.FindingDto> reconcile(AgentDtos.PatientFacts patient, List<AgentDtos.CurrentMed> currentMeds, List<String> herbs) {
        AgentDtos.ReconcileResult result = restClient.post()
                .uri("/agents/evaluator/reconcile")
                .body(new AgentDtos.ReconcileRequest(patient, currentMeds, herbs))
                .retrieve()
                .body(AgentDtos.ReconcileResult.class);
        return result == null || result.findings() == null ? List.of() : result.findings();
    }

    /** Speech to text. The raw recording goes as the request body; the text comes back for the doctor to check. */
    public String transcribe(byte[] audio, String filename, String language) {
        Map<?, ?> result = restClient.post()
                .uri(uri -> uri.path("/agents/transcribe").queryParam("language", language).queryParam("filename", filename).build())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(audio)
                .retrieve()
                .body(Map.class);
        return result == null || result.get("text") == null ? "" : result.get("text").toString();
    }

    /** Sends a user-consented packet image inline to the agent for ephemeral label extraction. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readMedicinePacket(byte[] image, String mimeType) {
        return restClient.post()
                .uri(uri -> uri.path("/agents/packet/read").queryParam("mime_type", mimeType).build())
                .contentType(MediaType.parseMediaType(mimeType))
                .header("X-Image-Consent-Confirmed", "true")
                .body(image)
                .retrieve()
                .body(Map.class);
    }

    /** The generics of medicine names and the herbs in remedy names, from the agents' drug data. */
    public AgentDtos.Normalised normalise(List<String> medicines, List<String> herbs) {
        return restClient.post()
                .uri("/agents/evaluator/normalise")
                .body(Map.of("medicines", medicines, "herbs", herbs))
                .retrieve()
                .body(AgentDtos.Normalised.class);
    }

    /** What the patient graph holds for one patient, as the agents read it. Throws if there is no graph or no such patient. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> graphContext(String graphId) {
        return restClient.get()
                .uri("/agents/graph/{graphId}/context", graphId)
                .retrieve()
                .body(Map.class);
    }
}
