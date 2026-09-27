package com.khabar.api.patients;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.account.id = :accountId")
    Optional<Patient> lockByAccountId(@Param("accountId") UUID accountId);

    List<Patient> findByClinicId(UUID clinicId);

    long countByClinicIdAndFollowUpStartIsNotNull(UUID clinicId);

    Optional<Patient> findFirstByPhoneIndex(String phoneIndex);
}
