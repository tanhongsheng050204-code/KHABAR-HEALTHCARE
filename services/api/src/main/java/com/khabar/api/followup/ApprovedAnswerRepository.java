package com.khabar.api.followup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovedAnswerRepository extends JpaRepository<ApprovedAnswer, UUID> {

    List<ApprovedAnswer> findByClinicIdAndRetiredAtIsNullOrderByApprovedAt(UUID clinicId);
}
