package com.khabar.api.followup;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CaseNoteRepository extends JpaRepository<CaseNote, UUID> {

    List<CaseNote> findByCaseIdOrderByAtAsc(UUID caseId);

    /** Notes in a clinic that mention a user, newest first. The mentions column holds user IDs only. */
    @Query("select n from CaseNote n where n.clinicId = :clinicId and n.mentions like concat('%', :userId, '%') order by n.at desc")
    List<CaseNote> mentioning(@Param("clinicId") UUID clinicId, @Param("userId") String userId, Pageable page);
}
