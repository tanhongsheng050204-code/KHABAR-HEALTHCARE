package com.khabar.api.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Boots the guarded pilot configuration against a throwaway PostgreSQL-mode H2 database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:khabar-pilot-schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "khabar.security.internal-service-key=test-internal-service-key",
        "khabar.security.supabase-jwt-secret=test-supabase-jwt-secret-with-at-least-32-bytes!!",
        "khabar.security.field-encryption-key=a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
        "khabar.services.agents-url=http://localhost:9",
        "khabar.web.allowed-origins=http://localhost:3000",
        "khabar.web.app-url=http://localhost:3000",
        "khabar.checkins.scheduler-enabled=false"
})
@ActiveProfiles("pilot")
class PilotSchemaSmokeTest {

    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void pilotMigratesAndValidatesTheApplicationSchemaBeforeStarting() {
        assertEquals(3, flyway.info().applied().length);
        assertNotNull(entityManagerFactory);
    }
}
