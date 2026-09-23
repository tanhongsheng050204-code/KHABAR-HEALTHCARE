package com.khabar.api.graph;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes one patient's facts to Neo4j in a single transaction: the patient's own relationships and
 * visit/reading nodes are removed and written again, so the graph always matches Postgres. Shared nodes
 * (conditions, medicines, brands, herbs, allergies, symptoms) are merged, never duplicated.
 *
 * <pre>
 * (:Patient {graph_id, pregnant})
 *   -[:HAS_CONDITION]->(:Condition {name})
 *   -[:ALLERGIC_TO]->(:Allergy {name})
 *   -[:TAKES {name, source, since}]->(:Medication {generic, recognised}) &lt;-[:BRAND_OF]-(:Brand {name})
 *   -[:USES {name, source, since}]->(:Herb {name, recognised})
 *   -[:HAD]->(:Encounter {id, at})-[:PRESCRIBED {name}]->(:Medication)
 *   -[:REPORTED {level, at}]->(:Symptom {word})
 *   -[:RECORDED]->(:Reading {id, kind, value, level, at})
 * </pre>
 * The agents' read queries (services/agents/data/graph_queries.json) depend on this shape.
 */
public class Neo4jPatientGraph implements PatientGraph {

    private static final List<String> CONSTRAINTS = List.of(
            "CREATE CONSTRAINT patient_graph_id IF NOT EXISTS FOR (n:Patient) REQUIRE n.graph_id IS UNIQUE",
            "CREATE CONSTRAINT condition_name IF NOT EXISTS FOR (n:Condition) REQUIRE n.name IS UNIQUE",
            "CREATE CONSTRAINT allergy_name IF NOT EXISTS FOR (n:Allergy) REQUIRE n.name IS UNIQUE",
            "CREATE CONSTRAINT medication_generic IF NOT EXISTS FOR (n:Medication) REQUIRE n.generic IS UNIQUE",
            "CREATE CONSTRAINT brand_name IF NOT EXISTS FOR (n:Brand) REQUIRE n.name IS UNIQUE",
            "CREATE CONSTRAINT herb_name IF NOT EXISTS FOR (n:Herb) REQUIRE n.name IS UNIQUE",
            "CREATE CONSTRAINT symptom_word IF NOT EXISTS FOR (n:Symptom) REQUIRE n.word IS UNIQUE");

    private static final String PATIENT = "MERGE (p:Patient {graph_id: $g}) SET p.pregnant = $pregnant";
    private static final String CLEAR_LINKS =
            "MATCH (:Patient {graph_id: $g})-[old:HAS_CONDITION|ALLERGIC_TO|TAKES|USES|REPORTED]->() DELETE old";
    private static final String CLEAR_OWN_NODES = "MATCH (:Patient {graph_id: $g})-[:HAD|RECORDED]->(n) DETACH DELETE n";
    private static final String CONDITIONS =
            "MATCH (p:Patient {graph_id: $g}) UNWIND $items AS name MERGE (c:Condition {name: name}) MERGE (p)-[:HAS_CONDITION]->(c)";
    private static final String ALLERGIES =
            "MATCH (p:Patient {graph_id: $g}) UNWIND $items AS name MERGE (a:Allergy {name: name}) MERGE (p)-[:ALLERGIC_TO]->(a)";
    private static final String MEDICINES = """
            MATCH (p:Patient {graph_id: $g}) UNWIND $items AS t
            MERGE (m:Medication {generic: t.key}) SET m.recognised = t.recognised
            CREATE (p)-[:TAKES {name: t.name, source: t.source, since: t.since}]->(m)
            FOREACH (brand IN CASE WHEN t.brand IS NULL THEN [] ELSE [t.brand] END |
                MERGE (b:Brand {name: brand}) MERGE (b)-[:BRAND_OF]->(m))""";
    private static final String HERBS = """
            MATCH (p:Patient {graph_id: $g}) UNWIND $items AS t
            MERGE (h:Herb {name: t.key}) SET h.recognised = t.recognised
            CREATE (p)-[:USES {name: t.name, source: t.source, since: t.since}]->(h)""";
    private static final String VISITS = """
            MATCH (p:Patient {graph_id: $g}) UNWIND $items AS v
            CREATE (p)-[:HAD]->(e:Encounter {id: v.id, at: v.at})
            WITH e, v UNWIND v.prescribed AS t
            MERGE (m:Medication {generic: t.key}) SET m.recognised = t.recognised
            CREATE (e)-[:PRESCRIBED {name: t.name}]->(m)""";
    private static final String SYMPTOMS = """
            MATCH (p:Patient {graph_id: $g}) UNWIND $items AS s
            MERGE (x:Symptom {word: s.word}) CREATE (p)-[:REPORTED {level: s.level, at: s.at}]->(x)""";
    private static final String READINGS = """
            MATCH (p:Patient {graph_id: $g}) UNWIND $items AS r
            CREATE (p)-[:RECORDED]->(:Reading {id: r.id, kind: r.kind, value: r.value, level: r.level, at: r.at})""";

    private final Driver driver;
    private final AtomicBoolean constraintsReady = new AtomicBoolean();

    public Neo4jPatientGraph(Driver driver) {
        this.driver = driver;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void close() {
        driver.close();
    }

    @Override
    public void write(GraphFacts facts) {
        ensureConstraints();
        try (Session session = driver.session()) {
            session.executeWriteWithoutResult(tx -> write(tx, facts));
        }
    }

    private void write(TransactionContext tx, GraphFacts facts) {
        String g = facts.graphId().toString();
        tx.run(PATIENT, Map.of("g", g, "pregnant", facts.pregnant()));
        tx.run(CLEAR_LINKS, Map.of("g", g));
        tx.run(CLEAR_OWN_NODES, Map.of("g", g));
        run(tx, CONDITIONS, g, facts.conditions());
        run(tx, ALLERGIES, g, facts.allergies());
        run(tx, MEDICINES, g, facts.medicines().stream().map(Neo4jPatientGraph::taken).toList());
        run(tx, HERBS, g, facts.herbs().stream().map(Neo4jPatientGraph::taken).toList());
        run(tx, VISITS, g, facts.visits().stream().map(v -> Map.of(
                "id", v.id().toString(), "at", time(v.at()),
                "prescribed", v.prescribed().stream().map(Neo4jPatientGraph::taken).toList())).toList());
        run(tx, SYMPTOMS, g, facts.symptoms().stream().map(s -> Map.of(
                "word", s.word(), "level", s.level(), "at", time(s.at()))).toList());
        run(tx, READINGS, g, facts.readings().stream().map(r -> Map.of(
                "id", r.id().toString(), "kind", r.kind(), "value", r.value(), "level", r.level(), "at", time(r.at()))).toList());
    }

    private static void run(TransactionContext tx, String query, String g, List<?> items) {
        if (!items.isEmpty()) {
            tx.run(query, Map.of("g", g, "items", items));
        }
    }

    private static Map<String, Object> taken(GraphFacts.Taken t) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", t.name());
        map.put("key", t.key());
        map.put("recognised", t.recognised());
        map.put("brand", t.brand());
        map.put("source", t.source());
        map.put("since", t.since() == null ? null : time(t.since()));
        return map;
    }

    private static OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private void ensureConstraints() {
        if (constraintsReady.get()) {
            return;
        }
        try (Session session = driver.session()) {
            CONSTRAINTS.forEach(c -> session.run(c).consume());
        }
        constraintsReady.set(true);
    }
}
