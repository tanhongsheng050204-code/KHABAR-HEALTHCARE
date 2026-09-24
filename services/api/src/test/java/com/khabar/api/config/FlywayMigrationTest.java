package com.khabar.api.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.sql.DatabaseMetaData;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationTest {

    @Test
    void pilotMigrationsCreateAnEmptyPostgresModeSchemaAndRunOnlyOnce() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:khabar_migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway flyway = flyway(dataSource);

        assertEquals(3, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);

        try (Connection connection = dataSource.getConnection();
             var columns = connection.getMetaData().getColumns(null, null, "READING", "RECEIVED_AT")) {
            assertTrue(columns.next(), "V2 must provision reading.received_at.");
            assertEquals(DatabaseMetaData.columnNoNulls, columns.getInt("NULLABLE"));
        }
    }

    @Test
    void receiveTimeMigrationBackfillsLegacyRowsFromTheirMeasurementTime() throws Exception {
        JdbcDataSource dataSource = dataSource();
        UUID clinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID readingId = UUID.randomUUID();
        Instant measuredAt = Instant.parse("2026-01-02T03:04:05Z");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement clinic = connection.prepareStatement("insert into clinic(id, name) values (?, ?)")) {
                clinic.setObject(1, clinicId);
                clinic.setString(2, "Migration test");
                clinic.executeUpdate();
            }
            try (PreparedStatement patient = connection.prepareStatement(
                    "insert into patient(id, clinic_id, graph_id, full_name, preferred_language, pregnant) values (?, ?, ?, ?, ?, ?)")) {
                patient.setObject(1, patientId);
                patient.setObject(2, clinicId);
                patient.setObject(3, UUID.randomUUID());
                patient.setString(4, "Synthetic Migration Patient");
                patient.setString(5, "en");
                patient.setBoolean(6, false);
                patient.executeUpdate();
            }
            try (PreparedStatement reading = connection.prepareStatement(
                    "insert into reading(id, patient_id, measured_at, source, description, kind, level) values (?, ?, ?, ?, ?, ?, ?)")) {
                reading.setObject(1, readingId);
                reading.setObject(2, patientId);
                reading.setTimestamp(3, Timestamp.from(measuredAt));
                reading.setString(4, "patient");
                reading.setString(5, "Synthetic migration-test reading");
                reading.setString(6, "GLUCOSE");
                reading.setString(7, "REVIEW");
                reading.executeUpdate();
            }
        }

        flyway(dataSource).migrate();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement("select measured_at, received_at from reading where id = ?")) {
            query.setObject(1, readingId);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(measuredAt, result.getObject("measured_at", java.time.OffsetDateTime.class).toInstant());
                assertEquals(measuredAt, result.getObject("received_at", java.time.OffsetDateTime.class).toInstant());
            }
        }
    }

    @Test
    void clinicRoleMigrationBackfillsExistingDoctorsAsAuditableGrants() throws Exception {
        JdbcDataSource dataSource = dataSource();
        UUID clinicId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1")).load().migrate();

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement clinic = connection.prepareStatement("insert into clinic(id, name) values (?, ?)")) {
                clinic.setObject(1, clinicId); clinic.setString(2, "Migration test clinic"); clinic.executeUpdate();
            }
            try (PreparedStatement doctor = connection.prepareStatement("insert into app_user(id, role, display_name, clinic_id) values (?, 'DOCTOR', ?, ?)")) {
                doctor.setObject(1, doctorId); doctor.setString(2, "Legacy doctor"); doctor.setObject(3, clinicId); doctor.executeUpdate();
            }
        }

        flyway(dataSource).migrate();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement grant = connection.prepareStatement("select role, granted_by, revoked_at from clinic_staff_grant where app_user_id = ? and clinic_id = ?")) {
            grant.setObject(1, doctorId); grant.setObject(2, clinicId);
            try (var result = grant.executeQuery()) {
                assertTrue(result.next(), "V3 must preserve existing doctors' clinic access.");
                assertEquals("DOCTOR", result.getString("role"));
                assertEquals(null, result.getObject("granted_by"), "The historical grantor is unknown.");
                assertEquals(null, result.getObject("revoked_at"));
            }
        }
    }

    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:khabar_migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static Flyway flyway(JdbcDataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
    }
}
