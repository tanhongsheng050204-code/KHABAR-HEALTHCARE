package com.khabar.api.encounters;

import com.khabar.api.service.AgentDtos.DraftedRx;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The independent check on a take-home plan: medicines and numbers must come from the prescription. */
class SummaryCheckTest {

    static final PrescriptionLine METFORMIN = PrescriptionLine.from(new DraftedRx("T. Metformin 500mg 1/1 BD PC", "Metformin", 500.0, 1.0, 2,
            List.of("morning", "night"), "after_food", false));
    static final PrescriptionLine AMLODIPINE = PrescriptionLine.from(new DraftedRx("T. Amlodipine 5mg OD", "Amlodipine", 5.0, 1.0, 1,
            List.of("morning"), null, false));

    @Test
    void theAgentsOwnPlanPassesInEveryLanguage() {
        String ms = "• Metformin 500 mg: 1 biji, pagi dan malam, selepas makan.\n"
                + "Kalau rasa lebih teruk atau ada tanda yang membimbangkan, datang ke klinik segera.\nDatang semula dalam 2 minggu.";
        String zh = "• Metformin 500 mg: 每次1粒，早上和晚上，饭后服用。\n如果感觉更不舒服或有担心的症状，请立即回诊所。\n2个星期后回来复诊。";
        String en = "• Metformin 500 mg: 1 tablet, morning and night, after food.\nCome back in 14 days.";
        assertThat(SummaryCheck.check(ms, List.of(METFORMIN), 2.0)).isEmpty();
        assertThat(SummaryCheck.check(zh, List.of(METFORMIN), 2.0)).isEmpty();
        assertThat(SummaryCheck.check(en, List.of(METFORMIN), 2.0)).isEmpty();
    }

    @Test
    void aDoseTheDoctorDidNotWriteIsHeld() {
        String text = "• Metformin 850 mg: 1 biji, pagi dan malam, selepas makan.";
        assertThat(SummaryCheck.check(text, List.of(METFORMIN), null))
                .anySatisfy(p -> assertThat(p).contains("850"));
    }

    @Test
    void anExtraOrMissingMedicineIsHeld() {
        String text = "• Metformin 500 mg: 1 biji, pagi dan malam.\n• Glibenclamide 5 mg: 1 biji setiap pagi.";
        List<String> problems = SummaryCheck.check(text, List.of(METFORMIN, AMLODIPINE), null);
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("Glibenclamide").contains("not in the prescription"));
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("Amlodipine").contains("missing from the plan"));
    }

    @Test
    void theEmergencyNumberIsAlwaysAllowed() {
        String text = "• Metformin 500 mg: 1 biji.\nIf it is an emergency, call 999.";
        assertThat(SummaryCheck.check(text, List.of(METFORMIN), null)).isEmpty();
    }

    @Test
    void anEmptyPlanIsHeld() {
        assertThat(SummaryCheck.check(" ", List.of(METFORMIN), null)).containsExactly("The plan is empty.");
    }
}
