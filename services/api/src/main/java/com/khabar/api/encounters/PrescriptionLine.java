package com.khabar.api.encounters;

import com.khabar.api.service.AgentDtos.DraftedRx;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Arrays;
import java.util.List;

/** One medicine on a visit's prescription, as parsed from the doctor's shorthand. */
@Embeddable
public class PrescriptionLine {

    @Column(length = 500)
    private String raw;
    private String name;
    private Double strengthMg;
    private Double unitsPerDose;
    private Integer timesPerDay;
    /** Comma-separated: morning, afternoon, evening, night, now. */
    private String timesOfDay;
    private String timing;
    private boolean asNeeded;

    protected PrescriptionLine() {
    }

    static PrescriptionLine from(DraftedRx rx) {
        PrescriptionLine line = new PrescriptionLine();
        line.raw = rx.raw();
        line.name = rx.name();
        line.strengthMg = rx.strengthMg();
        line.unitsPerDose = rx.unitsPerDose() == null ? 1.0 : rx.unitsPerDose();
        line.timesPerDay = rx.timesPerDay();
        line.timesOfDay = rx.timesOfDay() == null ? "" : String.join(",", rx.timesOfDay());
        line.timing = rx.timing();
        line.asNeeded = rx.asNeeded();
        return line;
    }

    DraftedRx toDto() {
        List<String> slots = timesOfDay == null || timesOfDay.isBlank() ? List.of() : Arrays.asList(timesOfDay.split(","));
        return new DraftedRx(raw, name, strengthMg, unitsPerDose, timesPerDay, slots, timing, asNeeded);
    }

    /** Milligrams per dose (strength × tablets), or null if the strength is unknown. */
    public Double doseMg() {
        return strengthMg == null ? null : strengthMg * (unitsPerDose == null ? 1 : unitsPerDose);
    }

    public String getRaw() {
        return raw;
    }

    public String getName() {
        return name;
    }

    public Double getStrengthMg() {
        return strengthMg;
    }

    public Double getUnitsPerDose() {
        return unitsPerDose;
    }

    public Integer getTimesPerDay() {
        return timesPerDay;
    }

    public String getTiming() {
        return timing;
    }

    public boolean isAsNeeded() {
        return asNeeded;
    }
}
