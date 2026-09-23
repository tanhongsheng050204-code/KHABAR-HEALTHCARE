package com.khabar.api.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.graph.GraphFacts.ReadingFact;
import com.khabar.api.graph.GraphFacts.Symptom;
import com.khabar.api.graph.GraphFacts.Taken;
import com.khabar.api.graph.GraphFacts.Visit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The writer against a real Neo4j running inside the test. The reads are the agents' own queries,
 * loaded from services/agents/data/graph_queries.json, so the writer and the reader cannot drift apart.
 */
class Neo4jPatientGraphTest {

    static Neo4j neo4j;
    static Driver driver;
    static JsonNode agentQueries;
    PatientGraph graph;

    static final Instant T = Instant.parse("2026-09-20T02:00:00Z");

    @BeforeAll
    static void start() throws Exception {
        neo4j = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
        driver = GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none());
        agentQueries = new ObjectMapper().readTree(Path.of("..", "agents", "data", "graph_queries.json").toFile());
    }

    @AfterAll
    static void stop() {
        driver.close();
        neo4j.close();
    }

    @BeforeEach
    void emptyGraph() {
        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }
        graph = new Neo4jPatientGraph(driver);
    }

    static GraphFacts aminah(UUID graphId) {
        return new GraphFacts(graphId, false, List.of("diabetes", "hypertension"), List.of(),
                List.of(new Taken("Metformin 500mg", "metformin", true, null, "Klinik Kesihatan", T),
                        new Taken("Brand A 500mg", "metformin", true, "brand a", "GP clinic", T.plusSeconds(1))),
                List.of(new Taken("Jus peria (bitter gourd)", "bitter gourd", true, null, "Her sister", T.plusSeconds(2))),
                List.of(new Visit(UUID.randomUUID(), T.plusSeconds(60), List.of(new Taken("Gliclazide 80mg", "gliclazide", true, null, null, null)))),
                List.of(new Symptom("pening", "watch", T.plusSeconds(120))),
                List.of(new ReadingFact(UUID.randomUUID(), "GLUCOSE", "Glucose 3.2 mmol/L", "RED", T.plusSeconds(180))));
    }

    List<Record> agentsRead(String query, UUID graphId) {
        try (Session session = driver.session()) {
            return session.run(agentQueries.get(query).asText(), Map.of("graph_id", graphId.toString())).list();
        }
    }

    @Test
    void theAgentsCanReadBackEverythingWrittenUsingOnlyTheGraphId() {
        UUID id = UUID.randomUUID();
        graph.write(aminah(id));

        assertThat(agentsRead("patient", id)).singleElement().satisfies(r -> assertThat(r.get("pregnant").asBoolean()).isFalse());
        assertThat(agentsRead("conditions", id)).extracting(r -> r.get("name").asString()).containsExactly("diabetes", "hypertension");
        assertThat(agentsRead("medicines", id)).extracting(r -> r.get("name").asString() + "|" + r.get("generic").asString() + "|" + r.get("source").asString())
                .containsExactly("Metformin 500mg|metformin|Klinik Kesihatan", "Brand A 500mg|metformin|GP clinic");
        assertThat(agentsRead("herbs", id)).extracting(r -> r.get("herb").asString()).containsExactly("bitter gourd");
        assertThat(agentsRead("last_visit", id)).singleElement().satisfies(r -> {
            assertThat(r.get("at").asString()).startsWith("2026-09-20T02:01");
            assertThat(r.get("prescribed").asList()).containsExactly("gliclazide");
        });
        assertThat(agentsRead("symptoms", id)).extracting(r -> r.get("word").asString() + "|" + r.get("level").asString()).containsExactly("pening|watch");
        assertThat(agentsRead("readings", id)).extracting(r -> r.get("value").asString()).containsExactly("Glucose 3.2 mmol/L");
        assertThat(agentsRead("allergies", id)).isEmpty();
    }

    @Test
    void aBrandPointsAtItsGenericAndTwoRoutesToOneDrugShareOneNode() {
        UUID id = UUID.randomUUID();
        graph.write(aminah(id));
        try (Session session = driver.session()) {
            Record r = session.run("""
                    MATCH (p:Patient {graph_id: $g})-[t:TAKES]->(m:Medication {generic: 'metformin'})
                    OPTIONAL MATCH (b:Brand)-[:BRAND_OF]->(m)
                    RETURN count(DISTINCT m) AS nodes, count(DISTINCT t) AS routes, collect(DISTINCT b.name) AS brands""",
                    Map.of("g", id.toString())).single();
            assertThat(r.get("nodes").asInt()).isEqualTo(1);
            assertThat(r.get("routes").asInt()).isEqualTo(2);
            assertThat(r.get("brands").asList()).containsExactly("brand a");
        }
    }

    @Test
    void writingAgainReplacesThePatientsFactsInsteadOfPilingUp() {
        UUID id = UUID.randomUUID();
        graph.write(aminah(id));
        GraphFacts later = new GraphFacts(id, true, List.of("diabetes"), List.of("penicillin"),
                List.of(new Taken("Metformin 500mg", "metformin", true, null, "Klinik Kesihatan", T)),
                List.of(), List.of(), List.of(), List.of());
        graph.write(later);

        assertThat(agentsRead("patient", id).get(0).get("pregnant").asBoolean()).isTrue();
        assertThat(agentsRead("conditions", id)).hasSize(1);
        assertThat(agentsRead("allergies", id)).extracting(r -> r.get("name").asString()).containsExactly("penicillin");
        assertThat(agentsRead("medicines", id)).hasSize(1);
        assertThat(agentsRead("herbs", id)).isEmpty();
        assertThat(agentsRead("last_visit", id)).allSatisfy(r -> assertThat(r.get("at").isNull()).isTrue());
        assertThat(agentsRead("readings", id)).isEmpty();
        try (Session session = driver.session()) {
            assertThat(session.run("MATCH (n:Encounter) RETURN count(n) AS n").single().get("n").asInt()).isZero();
            assertThat(session.run("MATCH (n:Reading) RETURN count(n) AS n").single().get("n").asInt()).isZero();
        }
    }

    @Test
    void patientsShareConditionAndMedicineNodesButNotEachOthersFacts() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        graph.write(aminah(first));
        graph.write(aminah(second));
        try (Session session = driver.session()) {
            assertThat(session.run("MATCH (n:Medication {generic: 'metformin'}) RETURN count(n) AS n").single().get("n").asInt()).isEqualTo(1);
            assertThat(session.run("MATCH (n:Condition) RETURN count(n) AS n").single().get("n").asInt()).isEqualTo(2);
        }
        graph.write(new GraphFacts(first, false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThat(agentsRead("medicines", second)).hasSize(2);
        assertThat(agentsRead("readings", second)).hasSize(1);
    }

    @Test
    void anUnrecognisedMedicineIsKeptButNotPassedOffAsAGeneric() {
        UUID id = UUID.randomUUID();
        graph.write(new GraphFacts(id, false, List.of(), List.of(),
                List.of(new Taken("Ubat X", "ubat x", false, null, "Pharmacy", T)), List.of(), List.of(), List.of(), List.of()));
        assertThat(agentsRead("medicines", id)).singleElement().satisfies(r -> assertThat(r.get("recognised").asBoolean()).isFalse());
    }
}
