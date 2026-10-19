package com.khabar.api.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientPreferencesRepository extends JpaRepository<PatientPreferences, UUID> {
}
