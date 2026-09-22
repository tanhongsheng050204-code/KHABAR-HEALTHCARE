package com.khabar.api.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** The JSON shapes exchanged with the Python agents service (snake_case on the wire). */
public final class AgentDtos {

    private AgentDtos() {
    }

    /** One prescription line as parsed by the agents' shorthand parser. */
    public record DraftedRx(
            String raw,
            String name,
            @JsonProperty("strength_mg") Double strengthMg,
            @JsonProperty("units_per_dose") Double unitsPerDose,
            @JsonProperty("times_per_day") Integer timesPerDay,
            @JsonProperty("times_of_day") List<String> timesOfDay,
            String timing,
            @JsonProperty("as_needed") boolean asNeeded) {
    }

    public record DraftedReport(
            String diagnosis,
            String plan,
            @JsonProperty("follow_up") String followUp,
            @JsonProperty("follow_up_weeks") Double followUpWeeks,
            @JsonProperty("warning_signs") List<String> warningSigns,
            List<DraftedRx> prescription) {
    }

    public record PatientFacts(List<String> allergies, boolean pregnant) {
    }

    public record CheckedRx(String name, @JsonProperty("dose_mg") Double doseMg, @JsonProperty("times_per_day") Integer timesPerDay) {
    }

    public record CurrentMed(String name, String source) {
    }

    /** What the evaluator checks: the draft report plus everything known about the patient. */
    public record SafetyDraft(
            PatientFacts patient,
            List<CheckedRx> prescription,
            @JsonProperty("current_meds") List<CurrentMed> currentMeds,
            List<String> herbs,
            Map<String, String> report,
            @JsonProperty("source_text") String sourceText) {
    }

    public record FindingDto(String check, String severity, String detail) {
    }

    public record SafetyCheckResult(boolean blocking, List<FindingDto> findings) {
    }

    public record MedicineLine(String medicine, String how) {
    }

    public record SummaryResult(
            String language,
            List<MedicineLine> medicines,
            String warning,
            @JsonProperty("next_visit") String nextVisit,
            @JsonProperty("needs_doctor") List<String> needsDoctor,
            String text) {
    }

    public record IntakeAnswer(String topic, String question, String answer) {
    }

    public record MedicineMention(@JsonAlias("as_written") String asWritten, String generic) {
    }

    public record IntakeFlag(String level, String matched) {
    }

    /** What the patient told the intake chat, laid out for the doctor. Read from the agents in snake_case, served in camelCase. */
    public record PreVisitReport(
            String reason,
            List<IntakeAnswer> answers,
            List<MedicineMention> medicines,
            List<String> herbs,
            List<String> allergies,
            @JsonAlias("ask_about") List<String> askAbout,
            @JsonAlias("red_flags") List<IntakeFlag> redFlags) {
    }

    /** A doctor-approved answer as offered to the matcher: an id and when to use it, never the answer text. */
    public record AnswerOption(String id, String title, List<String> triggers) {
    }

    public record AnswerMatch(@JsonAlias("answer_id") String answerId, String source) {
    }
}
