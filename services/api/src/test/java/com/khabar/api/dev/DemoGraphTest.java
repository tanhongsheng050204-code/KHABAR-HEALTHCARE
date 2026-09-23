package com.khabar.api.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos.Normalised;
import com.khabar.api.service.AgentDtos.WrittenAs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The demo clinic in the patient graph: Aminah's story, read back with the agents' own queries and only her graph ID. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DemoGraphTest {

    static final Neo4j NEO4J = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
    static final Driver DRIVER = GraphDatabase.driver(NEO4J.boltURI(), AuthTokens.none());

    @DynamicPropertySource
    static void graph(DynamicPropertyRegistry registry) {
        registry.add("khabar.graph.uri", () -> NEO4J.boltURI().toString());
        registry.add("khabar.graph.password", () -> "unused");
    }

    @AfterAll
    static void stop() {
        DRIVER.close();
    }

    @Autowired MockMvc mvc;
    @Autowired PatientRepository patients;
    @MockBean AgentClientService agents;

    JsonNode agentQueries;
    String aminahGraphId;

    @BeforeEach
    void fillTheGraph() throws Exception {
        agentQueries = new ObjectMapper().readTree(Path.of("..", "agents", "data", "graph_queries.json").toFile());
        when(agents.normalise(anyList(), anyList())).thenReturn(new Normalised(
                Map.of("Metformin 500mg", new WrittenAs("metformin", null), "Brand A 500mg", new WrittenAs("metformin", "brand a")),
                Map.of("Jus peria (bitter gourd)", "bitter gourd")));
        mvc.perform(post("/dev/graph/sync")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.written").value(30));
        Patient aminah = patients.findByAccountId(DemoData.AMINAH_ACCOUNT_ID).orElseThrow();
        aminahGraphId = aminah.getGraphId().toString();
    }

    List<Record> agentsRead(String query) {
        try (Session session = DRIVER.session()) {
            return session.run(agentQueries.get(query).asText(), Map.of("graph_id", aminahGraphId)).list();
        }
    }

    @Test
    void aminahsConditionsMedicinesAndHerbAreInTheGraph() {
        assertThat(agentsRead("conditions")).extracting(r -> r.get("name").asString()).containsExactly("diabetes", "hypertension");
        assertThat(agentsRead("medicines")).extracting(r -> r.get("generic").asString()).containsExactly("metformin", "metformin");
        assertThat(agentsRead("medicines")).extracting(r -> r.get("source").asString()).containsExactly("Klinik Kesihatan", "GP clinic");
        assertThat(agentsRead("herbs")).extracting(r -> r.get("herb").asString()).containsExactly("bitter gourd");
        assertThat(agentsRead("allergies")).isEmpty();
        assertThat(agentsRead("symptoms")).extracting(r -> r.get("word").asString()).contains("pening");
    }

    @Test
    void theGraphHoldsNoNameIcOrPhoneForAnyDemoPatient() {
        try (Session session = DRIVER.session()) {
            List<String> values = session.run("MATCH (n) RETURN properties(n) AS p UNION ALL MATCH ()-[r]->() RETURN properties(r) AS p").list()
                    .stream().flatMap(r -> r.get("p").asMap().values().stream()).map(String::valueOf).toList();
            assertThat(values).noneMatch(v -> v.contains("Aminah") || v.contains("Yusof") || v.contains("590312") || v.contains("03-0000"));
        }
    }

    @Test
    void resettingTheDemoPutsTheFollowUpRepliesBackInTheGraph() throws Exception {
        mvc.perform(post("/dev/demo/reset")).andExpect(status().isOk());
        try (Session session = DRIVER.session()) {
            long red = session.run("MATCH (:Patient)-[r:REPORTED {level: 'red'}]->(:Symptom {word: 'sakit dada'}) RETURN count(r) AS n")
                    .single().get("n").asLong();
            assertThat(red).isEqualTo(1);
        }
    }
}
