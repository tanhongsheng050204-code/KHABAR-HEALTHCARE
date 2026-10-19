package com.khabar.api.pathways;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.patients.Patient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Condition pathways: what a clinic follows for a patient with diabetes, high blood pressure or a recent operation.
 * Each pathway brings default targets (from Malaysian clinical practice guidelines, for the doctor to change), a
 * review interval, and one extra line in the daily check-in asking for what the pathway tracks.
 */
@Service
public class Pathways {

    public static final String DIABETES = "DIABETES";
    public static final String HYPERTENSION = "HYPERTENSION";
    public static final String POST_OP = "POST_OP";

    /** Upper targets; null means the pathway does not track it. */
    public record Targets(Double glucoseMax, Integer systolicMax, Integer diastolicMax, Double hba1cMax) {

        /** The stricter of two sets of targets, for a patient on more than one pathway. */
        Targets stricter(Targets other) {
            return new Targets(min(glucoseMax, other.glucoseMax), min(systolicMax, other.systolicMax),
                    min(diastolicMax, other.diastolicMax), min(hba1cMax, other.hba1cMax));
        }

        private static <T extends Comparable<T>> T min(T a, T b) {
            return a == null ? b : b == null ? a : a.compareTo(b) <= 0 ? a : b;
        }

        static final Targets NONE = new Targets(null, null, null, null);
    }

    public record Definition(String key, String label, Targets defaults, int reviewEveryWeeks, List<String> tracks) {
    }

    public static final Map<String, Definition> DEFINITIONS = definitions();

    private static Map<String, Definition> definitions() {
        Map<String, Definition> all = new LinkedHashMap<>();
        all.put(DIABETES, new Definition(DIABETES, "Type 2 diabetes", new Targets(7.0, null, null, 7.0), 12,
                List.of("Fasting blood sugar", "HbA1c", "Dose answers")));
        all.put(HYPERTENSION, new Definition(HYPERTENSION, "High blood pressure", new Targets(null, 140, 90, null), 12,
                List.of("Home blood pressure", "Dose answers")));
        all.put(POST_OP, new Definition(POST_OP, "After an operation", Targets.NONE, 1,
                List.of("Wound, pain and fever in check-in replies")));
        return all;
    }

    public record PathwayView(UUID id, String pathway, String label, Targets targets, int reviewEveryWeeks, LocalDate nextReviewOn,
                              boolean reviewDue, java.time.Instant startedAt, List<String> tracks) {
    }

    public record Input(String pathway, Double glucoseMax, Integer systolicMax, Integer diastolicMax, Double hba1cMax,
                        Integer reviewEveryWeeks, LocalDate nextReviewOn) {
    }

    private final PatientPathwayRepository pathways;
    private final AdjustableClock clock;

    public Pathways(PatientPathwayRepository pathways, AdjustableClock clock) {
        this.pathways = pathways;
        this.clock = clock;
    }

    public List<PatientPathway> active(UUID patientId) {
        return pathways.findByPatientIdAndEndedAtIsNullOrderByStartedAt(patientId);
    }

    public List<PathwayView> views(UUID patientId) {
        return active(patientId).stream().map(this::view).toList();
    }

    /** The targets that apply to a patient: the stricter value wherever two pathways overlap. */
    public Targets targets(UUID patientId) {
        return active(patientId).stream().map(PatientPathway::targets).reduce(Targets.NONE, Targets::stricter);
    }

    public boolean reviewDue(PatientPathway p) {
        return p.getNextReviewOn() != null && !p.getNextReviewOn().isAfter(LocalDate.now(clock));
    }

    public PathwayView start(Patient patient, AppUser doctor, Input input) {
        Definition definition = DEFINITIONS.get(input.pathway());
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose DIABETES, HYPERTENSION or POST_OP.");
        }
        if (active(patient.getId()).stream().anyMatch(p -> p.getPathway().equals(definition.key()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The patient is already on this pathway.");
        }
        int weeks = weeks(input.reviewEveryWeeks(), definition.reviewEveryWeeks());
        LocalDate next = input.nextReviewOn() != null ? input.nextReviewOn() : LocalDate.now(clock).plusWeeks(weeks);
        PatientPathway saved = pathways.save(new PatientPathway(patient.getId(), patient.getClinic().getId(), definition.key(),
                merge(definition.defaults(), input), weeks, next, doctor.getId(), clock.instant()));
        return view(saved);
    }

    public PathwayView update(PatientPathway pathway, Input input) {
        Definition definition = DEFINITIONS.get(pathway.getPathway());
        pathway.apply(merge(pathway.targets(), input));
        int weeks = weeks(input.reviewEveryWeeks(), pathway.getReviewEveryWeeks());
        pathway.schedule(weeks, input.nextReviewOn() != null ? input.nextReviewOn() : pathway.getNextReviewOn());
        return view(pathways.save(pathway));
    }

    public void end(PatientPathway pathway) {
        pathway.end(clock.instant());
        pathways.save(pathway);
    }

    private static int weeks(Integer given, int fallback) {
        if (given == null) {
            return fallback;
        }
        if (given < 1 || given > 52) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review every 1 to 52 weeks.");
        }
        return given;
    }

    private static Targets merge(Targets base, Input input) {
        Targets merged = new Targets(
                input.glucoseMax() != null ? input.glucoseMax() : base.glucoseMax(),
                input.systolicMax() != null ? input.systolicMax() : base.systolicMax(),
                input.diastolicMax() != null ? input.diastolicMax() : base.diastolicMax(),
                input.hba1cMax() != null ? input.hba1cMax() : base.hba1cMax());
        if ((merged.glucoseMax() != null && (merged.glucoseMax() < 4 || merged.glucoseMax() > 15))
                || (merged.systolicMax() != null && (merged.systolicMax() < 90 || merged.systolicMax() > 200))
                || (merged.diastolicMax() != null && (merged.diastolicMax() < 50 || merged.diastolicMax() > 120))
                || (merged.hba1cMax() != null && (merged.hba1cMax() < 5 || merged.hba1cMax() > 12))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A target is outside the range Khabar accepts.");
        }
        return merged;
    }

    private PathwayView view(PatientPathway p) {
        Definition d = DEFINITIONS.get(p.getPathway());
        return new PathwayView(p.getId(), p.getPathway(), d == null ? p.getPathway() : d.label(), p.targets(), p.getReviewEveryWeeks(),
                p.getNextReviewOn(), reviewDue(p), p.getStartedAt(), d == null ? List.of() : d.tracks());
    }

    // ---- The check-in's extra line ------------------------------------------------------------------------------

    private static final Map<String, Map<String, String>> ASK = Map.of(
            DIABETES, Map.of(
                    "ms", "Jika ada, masukkan bacaan gula darah anda hari ini dalam Khabar.",
                    "en", "If you can, add today's blood sugar reading in Khabar.",
                    "zh", "如有可能，请在 Khabar 记录今天的血糖读数。",
                    "ta", "முடிந்தால், இன்றைய இரத்தச் சர்க்கரை அளவை Khabar இல் பதிவு செய்யுங்கள்."),
            HYPERTENSION, Map.of(
                    "ms", "Jika ada, masukkan bacaan tekanan darah anda hari ini dalam Khabar.",
                    "en", "If you can, add today's blood pressure reading in Khabar.",
                    "zh", "如有可能，请在 Khabar 记录今天的血压读数。",
                    "ta", "முடிந்தால், இன்றைய இரத்த அழுத்த அளவை Khabar இல் பதிவு செய்யுங்கள்."),
            POST_OP, Map.of(
                    "ms", "Bagaimana luka pembedahan anda? Ada merah, bengkak, nanah atau demam?",
                    "en", "How is your wound? Any redness, swelling, discharge or fever?",
                    "zh", "伤口怎么样？有没有红、肿、流脓或发烧？",
                    "ta", "உங்கள் காயம் எப்படி இருக்கிறது? சிவப்பு, வீக்கம், சீழ் அல்லது காய்ச்சல் உள்ளதா?"));

    /** One line per pathway, added to the day's check-in; empty when the patient is on no pathway. */
    public String checkInExtra(UUID patientId, String language) {
        StringBuilder extra = new StringBuilder();
        for (PatientPathway p : active(patientId)) {
            Map<String, String> ask = ASK.get(p.getPathway());
            if (ask != null) {
                extra.append("\n").append(ask.getOrDefault(language, ask.get("en")));
            }
        }
        return extra.toString();
    }
}
