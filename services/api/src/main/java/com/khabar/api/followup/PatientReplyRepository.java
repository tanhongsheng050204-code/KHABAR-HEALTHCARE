package com.khabar.api.followup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatientReplyRepository extends JpaRepository<PatientReply, UUID> {

    List<PatientReply> findByPatientClinicIdAndHandledAtIsNull(UUID clinicId);

    List<PatientReply> findByPatientIdAndHandledAtIsNull(UUID patientId);
}
