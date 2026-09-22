package com.khabar.api.patients;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByAccountId(UUID accountId);

    List<Patient> findByClinicId(UUID clinicId);

    long countByClinicIdAndFollowUpStartIsNotNull(UUID clinicId);

    Optional<Patient> findFirstByPhoneIndex(String phoneIndex);
}
