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

    /** Returns {"level": "red|watch|ok|review", "matched": word or null}. Throws if the agents service is unreachable. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> triageReply(String text) {
        return restClient.post()
                .uri("/agents/followup/triage")
                .body(Map.of("text", text))
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> processIntake(String graphId, String preferredLanguage, List<Map<String, String>> messages) {
        Map<String, Object> payload = Map.of(
                "graph_id", graphId,
                "preferred_language", preferredLanguage,
                "messages", messages
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
}
