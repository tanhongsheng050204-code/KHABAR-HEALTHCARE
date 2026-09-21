package com.khabar.api.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByPatientIdOrderByIdDesc(UUID patientId);
}
