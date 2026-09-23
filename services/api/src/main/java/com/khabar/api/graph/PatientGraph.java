package com.khabar.api.graph;

/**
 * Where de-identified patient facts are written for the agents to read (Neo4j). Only Spring Boot writes;
 * the agents open the graph read-only. With no graph configured, nothing is written.
 */
public interface PatientGraph extends AutoCloseable {

    boolean enabled();

    /** Replaces everything the graph holds about this patient with these facts. */
    void write(GraphFacts facts);

    @Override
    default void close() {
    }

    PatientGraph NONE = new PatientGraph() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public void write(GraphFacts facts) {
        }
    };
}
