package com.khabar.api.graph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Everything the patient graph holds about one patient. There is deliberately no field for a name, IC
 * number or phone number: the patient is only the random graphId, and every piece of free text in here
 * has been through the Redactor first.
 */
public record GraphFacts(UUID graphId, boolean pregnant, List<String> conditions, List<String> allergies,
                         List<Taken> medicines, List<Taken> herbs, List<Visit> visits, List<Symptom> symptoms,
                         List<ReadingFact> readings) {

    /**
     * A medicine or remedy, as written, with the graph node it points at: its generic (or herb) when
     * the drug data knows it, otherwise the name as written, marked unrecognised.
     */
    public record Taken(String name, String key, boolean recognised, String brand, String source, Instant since) {
    }

    /** A finalised visit and what was prescribed at it. */
    public record Visit(UUID id, Instant at, List<Taken> prescribed) {
    }

    /** A warning word the patient used, in the pre-visit chat or a follow-up reply. */
    public record Symptom(String word, String level, Instant at) {
    }

    public record ReadingFact(UUID id, String kind, String value, String level, Instant at) {
    }

    /** Every piece of text that would be written, for the identity check before anything is sent. */
    public List<String> texts() {
        List<String> out = new ArrayList<>(conditions);
        out.addAll(allergies);
        Stream.concat(medicines.stream(), herbs.stream()).forEach(t -> add(out, t));
        visits.forEach(v -> v.prescribed().forEach(t -> add(out, t)));
        symptoms.forEach(s -> out.add(s.word()));
        readings.forEach(r -> out.add(r.value()));
        out.removeIf(s -> s == null);
        return out;
    }

    private static void add(List<String> out, Taken t) {
        out.add(t.name());
        out.add(t.key());
        out.add(t.brand());
        out.add(t.source());
    }
}
