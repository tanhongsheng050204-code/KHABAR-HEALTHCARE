package com.khabar.api.clinicops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClinicSettingsRepository extends JpaRepository<ClinicSettings, UUID> {
}
