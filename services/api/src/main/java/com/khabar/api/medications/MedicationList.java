package com.khabar.api.medications;

import com.khabar.api.service.AgentDtos.CurrentMed;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** A patient's active "what I take" list, in the shapes the safety checks want. */
@Component
public class MedicationList {

    static final String NO_SOURCE = "the patient's own list";

    private final MedicationItemRepository items;

    public MedicationList(MedicationItemRepository items) {
        this.items = items;
    }

    public List<MedicationItem> active(UUID patientId) {
        return items.findByPatientIdAndStoppedAtIsNullOrderByAddedAt(patientId);
    }

    public List<CurrentMed> currentMeds(UUID patientId) {
        return active(patientId).stream()
                .filter(item -> item.getKind() == MedicationItem.Kind.MEDICINE)
                .map(item -> new CurrentMed(item.getName(), item.getSource() == null ? NO_SOURCE : item.getSource()))
                .toList();
    }

    public List<String> herbs(UUID patientId) {
        return active(patientId).stream()
                .filter(item -> item.getKind() == MedicationItem.Kind.HERB)
                .map(MedicationItem::getName)
                .toList();
    }
}
