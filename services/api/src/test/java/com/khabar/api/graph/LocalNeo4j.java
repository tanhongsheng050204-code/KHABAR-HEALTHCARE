package com.khabar.api.graph;

import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

/**
 * A throwaway Neo4j on bolt://localhost:7687 for trying the patient graph without Docker or AuraDB.
 * No password (any will do); everything is lost when it stops. Run from services/api:
 *
 *   ./mvnw -q test-compile exec:java -Dexec.mainClass=com.khabar.api.graph.LocalNeo4j -Dexec.classpathScope=test
 */
public final class LocalNeo4j {

    private LocalNeo4j() {
    }

    public static void main(String[] args) throws InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7687;
        Neo4j neo4j = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withConfig(BoltConnector.listen_address, new SocketAddress("localhost", port))
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(neo4j::close));
        System.out.println("Neo4j for the patient graph is up at " + neo4j.boltURI() + " (Ctrl+C to stop)");
        Thread.currentThread().join();
    }
}
