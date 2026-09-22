package com.khabar.api.readings;

import com.khabar.api.followup.TriageLevel;

import java.util.Locale;

/**
 * How worrying a home reading is. SEED THRESHOLDS for the demo, taken from common guidance
 * (hypoglycaemia below 3.9 mmol/L and serious below 3.0; hypertensive crisis at 180/120);
 * a doctor must review them, and later set them per patient, before any real use.
 */
public final class ReadingLevels {

    public record Assessment(TriageLevel level, String description) {
    }

    private ReadingLevels() {
    }

    public static Assessment glucose(double mmol) {
        String value = "Blood sugar " + String.format(Locale.ROOT, "%.1f", mmol) + " mmol/L";
        if (mmol < 3.0) {
            return new Assessment(TriageLevel.RED, value + " (very low)");
        }
        if (mmol < 3.9) {
            return new Assessment(TriageLevel.WATCH, value + " (low)");
        }
        if (mmol > 16.7) {
            return new Assessment(TriageLevel.WATCH, value + " (very high)");
        }
        return new Assessment(TriageLevel.OK, value);
    }

    public static Assessment bloodPressure(int systolic, int diastolic) {
        String value = "Blood pressure " + systolic + "/" + diastolic;
        if (systolic >= 180 || diastolic >= 120) {
            return new Assessment(TriageLevel.RED, value + " (very high)");
        }
        if (systolic >= 160 || diastolic >= 100) {
            return new Assessment(TriageLevel.WATCH, value + " (high)");
        }
        if (systolic < 90) {
            return new Assessment(TriageLevel.WATCH, value + " (low)");
        }
        return new Assessment(TriageLevel.OK, value);
    }
}
