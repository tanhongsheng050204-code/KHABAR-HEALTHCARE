package com.khabar.api.clinicops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RotaEntryRepository extends JpaRepository<RotaEntry, UUID> {

    List<RotaEntry> findByClinicIdOrderByDayOfWeek(UUID clinicId);

    Optional<RotaEntry> findByClinicIdAndDayOfWeek(UUID clinicId, DayOfWeek dayOfWeek);

    void deleteByClinicId(UUID clinicId);
}
