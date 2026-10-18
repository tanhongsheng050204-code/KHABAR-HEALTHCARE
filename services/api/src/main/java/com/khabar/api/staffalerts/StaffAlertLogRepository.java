package com.khabar.api.staffalerts;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffAlertLogRepository extends JpaRepository<StaffAlertLog, Long> {

    List<StaffAlertLog> findByClinicIdOrderByIdDesc(UUID clinicId, Pageable page);
}
