package com.khabar.api.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByPatientIdOrderByIdDesc(UUID patientId);

    /** Entries not yet linked into the chain, oldest first. */
    List<AuditEntry> findByHashIsNullOrderByIdAsc(Pageable page);

    /** Sealed entries in chain order, from a position onwards. */
    List<AuditEntry> findByChainSeqGreaterThanEqualOrderByChainSeqAsc(long fromSeq, Pageable page);

    Optional<AuditEntry> findByChainSeq(long chainSeq);

    long countByHashIsNull();
}
