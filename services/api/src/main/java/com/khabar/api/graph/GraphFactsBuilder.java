package com.khabar.api.graph;

import com.khabar.api.encounters.Encounter;
import com.khabar.api.encounters.EncounterRepository;
import com.khabar.api.encounters.PrescriptionLine;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.graph.GraphFacts.ReadingFact;
import com.khabar.api.graph.GraphFacts.Symptom;
import com.khabar.api.graph.GraphFacts.Taken;
import com.khabar.api.graph.GraphFacts.Visit;
import com.khabar.api.intake.IntakeRecords;
import com.khabar.api.intake.IntakeSession;
import com.khabar.api.medications.MedicationItem;
import com.khabar.api.medications.MedicationItemRepository;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.Redactor;
import com.khabar.api.readings.ReadingRepository;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos.Normalised;
import com.khabar.api.service.AgentDtos.PreVisitReport;
import com.khabar.api.service.AgentDtos.WrittenAs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Turns what Postgres holds about a patient into the facts the graph gets. Every piece of free text goes
 * through the Redactor, and the patient appears only as their random graphId. Generic names and herbs
 * come from the agents' drug data; if the agents are unreachable, names are kept as written, unrecognised.
 */
@Component
public class GraphFactsBuilder {

    private static final Logger log = LoggerFactory.getLogger(GraphFactsBuilder.class);

    private final MedicationItemRepository medications;
    private final IntakeRecords intakes;
    private final EncounterRepository encounters;
    private final PatientReplyRepository replies;
    private final ReadingRepository readings;
    private final AgentClientService agents;

    public GraphFactsBuilder(MedicationItemRepository medications, IntakeRecords intakes, EncounterRepository encounters,
                             PatientReplyRepository replies, ReadingRepository readings, AgentClientService agents) {
        this.medications = medications;
        this.intakes = intakes;
        this.encounters = encounters;
        this.replies = replies;
        this.readings = readings;
        this.agents = agents;
    }

    public GraphFacts build(Patient patient) {
        List<MedicationItem> items = medications.findByPatientIdAndStoppedAtIsNullOrderByAddedAt(patient.getId());
        List<Encounter> visits = encounters.findByPatientIdAndStatusOrderByFinalisedAt(patient.getId(), Encounter.Status.FINAL);
        Optional<IntakeSession> intake = intakes.latestCompleted(patient.getId());
        PreVisitReport report = intake.map(intakes::report).orElse(null);

        List<String> medicineNames = Stream.concat(
                        items.stream().filter(i -> i.getKind() == MedicationItem.Kind.MEDICINE).map(MedicationItem::getName),
                        visits.stream().flatMap(v -> v.getPrescription().stream()).map(GraphFactsBuilder::nameOf))
                .map(name -> redact(name, patient)).distinct().toList();
        List<String> herbNames = items.stream().filter(i -> i.getKind() == MedicationItem.Kind.HERB)
                .map(i -> redact(i.getName(), patient)).distinct().toList();
        Normalised names = normalise(medicineNames, herbNames);

        List<Taken> medicines = new ArrayList<>();
        List<Taken> herbs = new ArrayList<>();
        for (MedicationItem item : items) {
            String name = redact(item.getName(), patient);
            String source = redact(item.getSource(), patient);
            if (item.getKind() == MedicationItem.Kind.HERB) {
                String herb = names.herbs().get(name);
                herbs.add(new Taken(name, herb != null ? herb : key(name), herb != null, null, source, item.getAddedAt()));
            } else {
                medicines.add(medicine(name, names, source, item.getAddedAt()));
            }
        }

        List<Visit> visitFacts = visits.stream().map(v -> new Visit(v.getId(), v.getFinalisedAt(), v.getPrescription().stream()
                .map(line -> medicine(redact(nameOf(line), patient), names, null, null)).toList())).toList();

        List<Symptom> symptoms = new ArrayList<>();
        if (report != null && report.redFlags() != null) {
            Instant at = intake.get().getCompletedAt();
            report.redFlags().forEach(f -> symptoms.add(new Symptom(redact(f.matched(), patient), f.level(), at)));
        }
        replies.findTop20ByPatientIdOrderByReceivedAtDesc(patient.getId()).stream()
                .filter(r -> r.getMatched() != null && (r.getLevel() == TriageLevel.RED || r.getLevel() == TriageLevel.WATCH))
                .forEach(r -> symptoms.add(new Symptom(redact(r.getMatched(), patient), r.getLevel().name().toLowerCase(Locale.ROOT), r.getReceivedAt())));

        List<ReadingFact> readingFacts = readings.findTop30ByPatientIdOrderByMeasuredAtDesc(patient.getId()).stream()
                .map(r -> new ReadingFact(r.getId(), r.getKind().name(), r.getDescription(), r.getLevel().name(), r.getMeasuredAt()))
                .toList();

        return new GraphFacts(patient.getGraphId(), patient.isPregnant(), conditions(report, patient), allergies(report, patient),
                medicines, herbs, visitFacts, symptoms, readingFacts);
    }

    private Normalised normalise(List<String> medicines, List<String> herbs) {
        if (medicines.isEmpty() && herbs.isEmpty()) {
            return new Normalised(Map.of(), Map.of());
        }
        try {
            Normalised result = agents.normalise(medicines, herbs);
            if (result != null && result.medicines() != null && result.herbs() != null) {
                return result;
            }
        } catch (RuntimeException e) {
            log.warn("Drug names not normalised for the patient graph; writing them as unrecognised: {}", e.getMessage());
        }
        return new Normalised(Map.of(), Map.of());
    }

    private static Taken medicine(String name, Normalised names, String source, Instant since) {
        WrittenAs as = names.medicines().get(name);
        boolean known = as != null && as.generic() != null;
        return new Taken(name, known ? as.generic() : key(name), known, known ? as.brand() : null, source, since);
    }

    private static List<String> conditions(PreVisitReport report, Patient patient) {
        if (report == null || report.conditions() == null) {
            return List.of();
        }
        return report.conditions().stream().map(c -> redact(c, patient)).distinct().toList();
    }

    private static List<String> allergies(PreVisitReport report, Patient patient) {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        patient.allergyList().forEach(a -> all.add(key(redact(a, patient))));
        if (report != null && report.allergies() != null) {
            report.allergies().forEach(a -> all.add(key(redact(a, patient))));
        }
        all.remove("");
        return List.copyOf(all);
    }

    private static String nameOf(PrescriptionLine line) {
        return line.getName() != null ? line.getName() : line.getRaw();
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static String redact(String text, Patient patient) {
        return text == null ? null : Redactor.redact(text, patient);
    }
}
