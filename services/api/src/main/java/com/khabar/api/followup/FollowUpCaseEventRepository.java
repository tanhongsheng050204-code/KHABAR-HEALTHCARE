package com.khabar.api.followup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FollowUpCaseEventRepository extends JpaRepository<FollowUpCaseEvent, Long> {

    List<FollowUpCaseEvent> findByCaseIdOrderByIdAsc(UUID caseId);
}
