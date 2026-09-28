from agents.report_agent import draft_from_notes

NOTES = """Pt c/o giddiness x 3/7, sweating before lunch.
Dx: T2DM with hypoglycaemia, HTN
Rx
1. T. Metformin 500mg 1/1 BD PC
2. T. Amlodipine 5mg 1/1 OD
3. T. Gliclazide 80mg 1/1 OM AC
Plan: reduce gliclazide if FBS < 4
Hypo sx -> RTC STAT
TCA 2/52 FBS, HbA1c"""


def test_drafts_the_structured_report_from_shorthand_notes():
    draft = draft_from_notes(NOTES)
    assert draft.diagnosis == "T2DM with hypoglycaemia, HTN"
    assert draft.plan == "reduce gliclazide if FBS < 4"
    assert [rx.name for rx in draft.prescription] == ["Metformin", "Amlodipine", "Gliclazide"]
    assert draft.prescription[0].times_per_day == 2


def test_follow_up_is_read_from_tca():
    draft = draft_from_notes(NOTES)
    assert draft.follow_up == "TCA 2/52 FBS, HbA1c"
    assert draft.follow_up_weeks == 2


def test_return_advice_is_kept_as_a_warning_sign():
    draft = draft_from_notes(NOTES)
    assert draft.warning_signs == ["Hypo sx -> RTC STAT"]


def test_missing_sections_stay_empty_rather_than_invented():
    draft = draft_from_notes("T. Amlodipine 5mg OD")
    assert draft.diagnosis == ""
    assert draft.plan == ""
    assert draft.follow_up == ""
    assert len(draft.prescription) == 1


# The example placeholder in web/components/clinical/visit-workspace.tsx. Doctors copy its
# shape, so every line of it must land in a field; keep the two in step.
NOTES_EXAMPLE_SHOWN_TO_DOCTORS = (
    "Dx: T2DM, BP stable\n"
    "T. Metformin 500mg 1/1 BD PC\n"
    "Plan: continue, keep a home BP log\n"
    "TCA 2/52 FBS\n"
    "RTC if chest pain or fainting"
)


def test_the_notes_example_shown_to_doctors_fills_every_field():
    draft = draft_from_notes(NOTES_EXAMPLE_SHOWN_TO_DOCTORS)
    assert draft.diagnosis == "T2DM, BP stable"
    assert draft.plan == "continue, keep a home BP log"
    assert draft.follow_up_weeks == 2
    assert draft.warning_signs == ["RTC if chest pain or fainting"]
    assert [(rx.name, rx.strength_mg, rx.times_per_day, rx.timing) for rx in draft.prescription] == [
        ("Metformin", 500.0, 2, "after_food")
    ]


# What Groq Whisper returned on 28 Sep for a fictional dictated note: prose, one line.
DICTATED = ("Diagnosis, Type 2 Diabetes. Tablet metformin 500 mg, twice daily, after meals. "
            "Review in 2 weeks, fasting blood sugar. Return if chest pain or fainting.")


def test_a_dictated_note_is_structured_like_typed_shorthand():
    draft = draft_from_notes(DICTATED)
    assert draft.diagnosis == "Type 2 Diabetes."
    assert [(rx.name, rx.strength_mg, rx.times_per_day, rx.timing) for rx in draft.prescription] == [
        ("metformin", 500.0, 2, "after_food")
    ]
    assert draft.follow_up_weeks == 2
    assert len(draft.warning_signs) == 1 and "chest pain or fainting" in draft.warning_signs[0]


def test_spoken_frequencies_timings_and_forms_become_codes():
    draft = draft_from_notes(
        "Capsule omeprazole 20 milligrams once daily before meals.\n"
        "Tablet amlodipine 5 mg at night.\n"
        "Tablet paracetamol 1 gram three times a day as needed.\n"
        "Tablet metformin 500 mg, two tablets twice a day after food."
    )
    assert [(rx.name, rx.strength_mg, rx.units_per_dose, rx.times_per_day, rx.timing, rx.as_needed)
            for rx in draft.prescription] == [
        ("omeprazole", 20.0, 1, 1, "before_food", False),
        ("amlodipine", 5.0, 1, 1, None, False),
        ("paracetamol", 1000.0, 1, 3, None, True),
        ("metformin", 500.0, 2, 2, "after_food", False),
    ]


def test_spoken_follow_up_in_days_and_come_back_wording():
    assert draft_from_notes("Come back in 3 days for a wound check.").follow_up_weeks == 3 / 7
    assert draft_from_notes("Follow up in two weeks.").follow_up_weeks == 2


def test_the_doctors_own_words_in_labelled_lines_are_left_alone():
    draft = draft_from_notes("Plan: Stop bitter gourd juice. Walk twice daily after meals.")
    assert draft.plan == "Stop bitter gourd juice. Walk twice daily after meals."
    assert draft.prescription == []


def test_typed_shorthand_with_abbreviations_is_not_split():
    draft = draft_from_notes("T. Metformin 500mg 1/1 BD PC")
    assert [(rx.name, rx.times_per_day) for rx in draft.prescription] == [("Metformin", 2)]


def test_two_typed_prescriptions_on_one_line_are_both_read():
    draft = draft_from_notes("T. Metformin 500mg BD PC. T. Amlodipine 5mg OD.")
    assert [rx.name for rx in draft.prescription] == ["Metformin", "Amlodipine"]


def test_spoken_frequency_words_outside_a_medicine_order_do_not_create_a_prescription():
    draft = draft_from_notes("Check BP daily.\nWalk twice a day after meals.")
    assert draft.prescription == []


def test_typed_shorthand_keeps_the_doctors_exact_text():
    draft = draft_from_notes("Metformin 500 mg BD, TCA 2/52")
    assert [rx.raw for rx in draft.prescription] == ["Metformin 500 mg BD, TCA 2/52"]
