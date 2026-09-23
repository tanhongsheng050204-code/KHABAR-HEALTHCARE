package com.khabar.api.graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * The patient graph is on when NEO4J_URI is set (bolt://... locally, neo4j+s://... for AuraDB), and off
 * otherwise: then nothing is written and the agents work from what Spring Boot sends them.
 */
@Configuration
public class GraphConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphConfig.class);

    @Bean
    public PatientGraph patientGraph(@Value("${khabar.graph.uri:}") String uri,
                                     @Value("${khabar.graph.username:neo4j}") String username,
                                     @Value("${khabar.graph.password:}") String password) {
        if (uri.isBlank()) {
            log.info("Patient graph not configured (NEO4J_URI is empty): nothing will be written to Neo4j.");
            return PatientGraph.NONE;
        }
        Config config = Config.builder()
                .withConnectionTimeout(5, TimeUnit.SECONDS)
                .withMaxConnectionPoolSize(5)
                .build();
        Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
        return new Neo4jPatientGraph(driver);
    }
}
