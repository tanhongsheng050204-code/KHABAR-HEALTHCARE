package com.khabar.api.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Optional real-PostgreSQL migration smoke test, enabled only in the isolated CI database job. */
@SpringBootTest(properties = {
        "spring.datasource.url=${KHABAR_POSTGRES_TEST_URL}",
        "spring.datasource.username=${KHABAR_POSTGRES_TEST_USERNAME}",
        "spring.datasource.password=${KHABAR_POSTGRES_TEST_PASSWORD}",
        "khabar.security.internal-service-key=test-only-postgres-smoke-key",
        "khabar.security.supabase-jwt-secret=test-supabase-jwt-secret-with-at-least-32-bytes!!",
        "khabar.security.field-encryption-key=a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
        "khabar.services.agents-url=http://localhost:9",
        "khabar.web.allowed-origins=http://localhost:3000",
        "khabar.web.app-url=http://localhost:3000",
        "khabar.checkins.scheduler-enabled=false"
})
@ActiveProfiles("pilot")
@EnabledIfSystemProperty(named = "khabar.postgres-smoke", matches = "true")
class PostgresPilotSchemaSmokeTest {

    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void pilotMigrationsRunAndHibernateValidatesAgainstPostgres() {
        assertEquals(3, flyway.info().applied().length);
        assertNotNull(entityManagerFactory);
    }
}
