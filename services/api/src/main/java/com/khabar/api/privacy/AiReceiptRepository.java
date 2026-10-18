package com.khabar.api.privacy;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiReceiptRepository extends JpaRepository<AiReceipt, Long> {

    List<AiReceipt> findByPatientIdOrderByIdDesc(UUID patientId, Pageable page);

    List<AiReceipt> findByPatientIdOrderByIdAsc(UUID patientId);

    void deleteByPatientId(UUID patientId);
}
