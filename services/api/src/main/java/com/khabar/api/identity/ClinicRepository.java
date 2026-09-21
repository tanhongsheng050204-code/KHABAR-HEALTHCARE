package com.khabar.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClinicRepository extends JpaRepository<Clinic, UUID> {
}
