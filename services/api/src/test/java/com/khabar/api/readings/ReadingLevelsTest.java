package com.khabar.api.readings;

import com.khabar.api.followup.TriageLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingLevelsTest {

    @Test
    void veryLowBloodSugarIsRed() {
        ReadingLevels.Assessment a = ReadingLevels.glucose(2.8);
        assertThat(a.level()).isEqualTo(TriageLevel.RED);
        assertThat(a.description()).isEqualTo("Blood sugar 2.8 mmol/L (very low)");
    }

    @Test
    void lowOrVeryHighBloodSugarIsWatch() {
        assertThat(ReadingLevels.glucose(3.5).level()).isEqualTo(TriageLevel.WATCH);
        assertThat(ReadingLevels.glucose(18.0).level()).isEqualTo(TriageLevel.WATCH);
    }

    @Test
    void ordinaryBloodSugarIsOk() {
        assertThat(ReadingLevels.glucose(6.4).level()).isEqualTo(TriageLevel.OK);
    }

    @Test
    void aHypertensiveCrisisIsRed() {
        assertThat(ReadingLevels.bloodPressure(185, 100).level()).isEqualTo(TriageLevel.RED);
        assertThat(ReadingLevels.bloodPressure(150, 122).level()).isEqualTo(TriageLevel.RED);
        assertThat(ReadingLevels.bloodPressure(185, 100).description()).isEqualTo("Blood pressure 185/100 (very high)");
    }

    @Test
    void highOrLowBloodPressureIsWatch() {
        assertThat(ReadingLevels.bloodPressure(165, 95).level()).isEqualTo(TriageLevel.WATCH);
        assertThat(ReadingLevels.bloodPressure(85, 55).level()).isEqualTo(TriageLevel.WATCH);
    }

    @Test
    void ordinaryBloodPressureIsOk() {
        assertThat(ReadingLevels.bloodPressure(128, 82).level()).isEqualTo(TriageLevel.OK);
    }
}
