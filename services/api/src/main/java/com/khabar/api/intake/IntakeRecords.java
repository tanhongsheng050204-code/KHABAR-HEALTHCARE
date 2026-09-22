package com.khabar.api.intake;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.identity.AppUser;
import com.khabar.api.medications.MedicationItem;
import com.khabar.api.medications.MedicationItemRepository;
import com.khabar.api.patients.Patient;
import com.khabar.api.service.AgentDtos.PreVisitReport;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reading and writing stored intakes, and passing what the patient said on to the rest of the record. */
@Component
public class IntakeRecords {

    static final String INTAKE_SOURCE = "Told Khabar at intake";

    private final IntakeSessionRepository sessions;
    private final MedicationItemRepository medications;
    private final ObjectMapper json;

    public IntakeRecords(IntakeSessionRepository sessions, MedicationItemRepository medications, ObjectMapper json) {
        this.sessions = sessions;
        this.medications = medications;
        this.json = json;
    }

    public Optional<IntakeSession> latestCompleted(UUID patientId) {
        return sessions.findFirstByPatientIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(patientId);
    }

    public PreVisitReport report(IntakeSession session) {
        if (session.getReportJson() == null) {
            return null;
        }
        try {
            return json.readValue(session.getReportJson(), PreVisitReport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored pre-visit report is unreadable", e);
        }
    }

    public List<Map<String, String>> transcript(IntakeSession session) {
        try {
            return json.readValue(session.getTranscriptJson(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored intake transcript is unreadable", e);
        }
    }

    public String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Allergies the patient mentioned in their latest intake, for the safety check. */
    public List<String> reportedAllergies(UUID patientId) {
        return latestCompleted(patientId)
                .map(this::report)
                .map(PreVisitReport::allergies)
                .orElse(List.of());
    }

    /** Medicines and herbs from the intake go on the patient's list, where the doctor sees them and the safety check reads them. */
    public void addToMedicationList(Patient patient, PreVisitReport report, AppUser patientAccount, Instant now) {
        Set<String> listed = medications.findByPatientIdAndStoppedAtIsNullOrderByAddedAt(patient.getId()).stream()
                .map(item -> item.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (report.medicines() != null) {
            report.medicines().forEach(m -> add(patient, m.asWritten(), MedicationItem.Kind.MEDICINE, listed, patientAccount, now));
        }
        if (report.herbs() != null) {
            report.herbs().forEach(h -> add(patient, h, MedicationItem.Kind.HERB, listed, patientAccount, now));
        }
    }

    private void add(Patient patient, String name, MedicationItem.Kind kind, Set<String> listed, AppUser by, Instant now) {
        if (name == null || name.isBlank() || !listed.add(name.toLowerCase(Locale.ROOT))) {
            return;
        }
        medications.save(new MedicationItem(patient, name.trim(), kind, INTAKE_SOURCE, by.getRole(), by.getId(), now));
    }
}
