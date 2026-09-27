package com.khabar.api.clinicops;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClinicActivityRepository extends JpaRepository<ClinicActivity, Long> {

    List<ClinicActivity> findByClinicIdOrderByIdDesc(UUID clinicId, Pageable page);
}
