package com.khabar.api.followup;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpCaseRepository extends JpaRepository<FollowUpCase, UUID> {

    Optional<FollowUpCase> findByPatientIdAndClosedAtIsNull(UUID patientId);

    List<FollowUpCase> findByClinicIdAndClosedAtIsNull(UUID clinicId);

    List<FollowUpCase> findByClosedAtIsNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FollowUpCase c where c.id = :id")
    Optional<FollowUpCase> lockById(@Param("id") UUID id);
}
