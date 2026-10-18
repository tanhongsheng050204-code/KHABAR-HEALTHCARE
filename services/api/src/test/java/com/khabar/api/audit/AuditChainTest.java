package com.khabar.api.audit;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.Role;
import com.khabar.api.service.AgentClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The record-access log as a hash chain: sealing, verification, tamper detection and public anchors. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditChainTest {

    @Autowired MockMvc mvc;
    @Autowired AuditLog auditLog;
    @Autowired AuditChain chain;
    @Autowired AuditEntryRepository entries;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired ClinicStaffAccess staffAccess;
    @MockBean AgentClientService agents;

    AppUser doctor, nurse;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Rantai"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, doctor.getId(), clock.instant());
        nurse = users.save(new AppUser(UUID.randomUUID(), Role.NURSE, "Nurse Mei", clinic));
        staffAccess.grant(nurse, clinic, ClinicStaffRole.NURSE, doctor.getId(), clock.instant());
    }

    @Test
    void newEntriesAreSealedInOrderAndTheChainVerifies() {
        UUID patient = UUID.randomUUID();
        auditLog.record(doctor, patient, AuditAction.VIEWED_RECORD);
        auditLog.record(nurse, patient, AuditAction.VIEWED_READINGS);
        chain.sealAll();

        var sealed = entries.findByPatientIdOrderByIdDesc(patient);
        assertThat(sealed).allSatisfy(e -> assertThat(e.getHash()).hasSize(64));
        AuditEntry older = sealed.get(1);
        AuditEntry newer = sealed.get(0);
        assertThat(newer.getChainSeq()).isGreaterThan(older.getChainSeq());
        assertThat(newer.getPrevHash()).isEqualTo(older.getHash());

        AuditChain.Verification result = chain.verify();
        assertThat(result.intact()).isTrue();
        assertThat(result.unsealed()).isZero();
        assertThat(result.checked()).isEqualTo(result.tailSeq());
    }

    @Test
    void editingASealedEntryIsReportedAtThatPosition() {
        UUID patient = UUID.randomUUID();
        auditLog.record(doctor, patient, AuditAction.VIEWED_RECORD);
        chain.sealAll();
        AuditEntry entry = entries.findByPatientIdOrderByIdDesc(patient).get(0);

        jdbc.update("update audit_log set actor_label = ? where id = ?", "Someone else", entry.getId());
        try {
            AuditChain.Verification result = chain.verify();
            assertThat(result.intact()).isFalse();
            assertThat(result.brokenAt()).isEqualTo(entry.getChainSeq());
            assertThat(result.problem()).contains("changed after it was sealed");
        } finally {
            jdbc.update("update audit_log set actor_label = ? where id = ?", entry.getActorLabel(), entry.getId());
        }
        assertThat(chain.verify().intact()).isTrue();
    }

    @Test
    void deletingASealedEntryBreaksTheChain() {
        UUID patient = UUID.randomUUID();
        auditLog.record(doctor, patient, AuditAction.VIEWED_RECORD);
        auditLog.record(doctor, patient, AuditAction.VIEWED_SUMMARY);
        chain.sealAll();
        AuditEntry first = entries.findByPatientIdOrderByIdDesc(patient).get(1);
        var row = jdbc.queryForMap("select * from audit_log where id = ?", first.getId());

        jdbc.update("delete from audit_log where id = ?", first.getId());
        try {
            AuditChain.Verification result = chain.verify();
            assertThat(result.intact()).isFalse();
            assertThat(result.brokenAt()).isEqualTo(first.getChainSeq());
        } finally {
            jdbc.update("insert into audit_log (id, patient_id, actor_id, actor_label, action, at, chain_seq, prev_hash, hash) values (?,?,?,?,?,?,?,?,?)",
                    row.get("ID"), row.get("PATIENT_ID"), row.get("ACTOR_ID"), row.get("ACTOR_LABEL"), row.get("ACTION"),
                    row.get("AT"), row.get("CHAIN_SEQ"), row.get("PREV_HASH"), row.get("HASH"));
        }
        assertThat(chain.verify().intact()).isTrue();
    }

    @Test
    void anchorsArePublicAndNameNoOne() throws Exception {
        auditLog.record(doctor, UUID.randomUUID(), AuditAction.VIEWED_RECORD);
        chain.sealAll();
        AuditChain.Anchor anchor = chain.anchor();

        mvc.perform(get("/api/audit/anchor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seq").value(anchor.seq()))
                .andExpect(jsonPath("$.hash").value(anchor.hash()))
                .andExpect(jsonPath("$.patientId").doesNotExist());
        mvc.perform(get("/api/audit/anchor/" + anchor.seq()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hash").value(anchor.hash()));
        mvc.perform(get("/api/audit/anchor/" + (anchor.seq() + 1000)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyClinicManagersCanVerifyTheWholeChain() throws Exception {
        mvc.perform(get("/api/clinic/audit/verify").header("Authorization", bearer(nurse.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clinic/audit/verify").header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(true));
    }
}
