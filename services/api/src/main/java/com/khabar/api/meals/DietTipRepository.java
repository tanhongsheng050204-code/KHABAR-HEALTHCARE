package com.khabar.api.meals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DietTipRepository extends JpaRepository<DietTip, UUID> {

    List<DietTip> findByClinicIdAndRetiredAtIsNullOrderByCategory(UUID clinicId);
}
