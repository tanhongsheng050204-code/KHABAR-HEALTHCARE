package com.khabar.api.dev;

import com.khabar.api.graph.PatientGraph;
import com.khabar.api.graph.PatientGraphSync;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * `local` profile only: fill the patient graph from Postgres, and see a patient's graph context exactly
 * as the agents read it (through the agents service, by graph ID).
 */
@RestController
@RequestMapping("/dev/graph")
@Profile("local")
public class DevGraphController {

    private final PatientGraph graph;
    private final PatientGraphSync sync;
    private final PatientRepository patients;
    private final AgentClientService agents;

    public DevGraphController(PatientGraph graph, PatientGraphSync sync, PatientRepository patients, AgentClientService agents) {
        this.graph = graph;
        this.sync = sync;
        this.patients = patients;
        this.agents = agents;
    }

    @PostMapping("/sync")
    public Map<String, Object> syncAll() {
        return Map.of("enabled", graph.enabled(), "written", sync.syncAll());
    }

    @GetMapping("/patients/{patientId}")
    @Transactional(readOnly = true)
    public Map<String, Object> asTheAgentsReadIt(@PathVariable UUID patientId) {
        if (!graph.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No patient graph is configured (set NEO4J_URI).");
        }
        String graphId = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .getGraphId().toString();
        return Map.of("graphId", graphId, "context", agents.graphContext(graphId));
    }
}
