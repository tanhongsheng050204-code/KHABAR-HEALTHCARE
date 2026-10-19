package com.khabar.api.caregivers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaregiverNoticeRepository extends JpaRepository<CaregiverNotice, UUID> {

    boolean existsByCaregiverIdAndKindAndRef(UUID caregiverId, String kind, UUID ref);

    List<CaregiverNotice> findTop20ByCaregiverIdOrderBySentAtDesc(UUID caregiverId);
}
