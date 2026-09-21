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
}
