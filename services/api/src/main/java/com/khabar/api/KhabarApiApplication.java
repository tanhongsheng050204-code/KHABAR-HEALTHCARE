package com.khabar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;

// The patient graph's Neo4j driver is made by GraphConfig, and only when NEO4J_URI is set.
@SpringBootApplication(exclude = Neo4jAutoConfiguration.class)
public class KhabarApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KhabarApiApplication.class, args);
    }
}

