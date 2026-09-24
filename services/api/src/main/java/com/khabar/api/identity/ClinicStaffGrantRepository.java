package com.khabar.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicStaffGrantRepository extends JpaRepository<ClinicStaffGrant, UUID> {

    boolean existsByAppUserIdAndClinicId(UUID userId, UUID clinicId);

    boolean existsByAppUserIdAndClinicIdAndRoleAndRevokedAtIsNull(UUID userId, UUID clinicId, ClinicStaffRole role);

    Optional<ClinicStaffGrant> findByAppUserIdAndClinicIdAndRoleAndRevokedAtIsNull(UUID userId, UUID clinicId, ClinicStaffRole role);

    List<ClinicStaffGrant> findByClinicIdAndRevokedAtIsNullOrderByGrantedAt(UUID clinicId);

    List<ClinicStaffGrant> findByAppUserIdAndClinicIdAndRevokedAtIsNullOrderByGrantedAt(UUID userId, UUID clinicId);

    Optional<ClinicStaffGrant> findByIdAndClinicIdAndRevokedAtIsNull(UUID id, UUID clinicId);

    long countByClinicIdAndRoleAndRevokedAtIsNull(UUID clinicId, ClinicStaffRole role);
}
