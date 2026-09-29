import pytest

from agents.prescription import parse_line


def test_parses_a_typical_clinic_line():
    rx = parse_line("T. Metformin 500mg 1/1 BD PC")
    assert rx.name == "Metformin"
    assert rx.strength_mg == 500
    assert rx.units_per_dose == 1
    assert rx.times_per_day == 2
    assert rx.timing == "after_food"
    assert rx.times_of_day == ["morning", "night"]
    assert rx.dose_mg == 500


def test_two_tablets_double_the_dose():
    rx = parse_line("T. Paracetamol 500mg 2/1 TDS")
    assert rx.units_per_dose == 2
    assert rx.dose_mg == 1000
    assert rx.times_per_day == 3


def test_grams_are_converted_to_milligrams():
    assert parse_line("Paracetamol 1g QID").strength_mg == 1000


@pytest.mark.parametrize("code, times, when", [
    ("OD", 1, ["morning"]), ("OM", 1, ["morning"]), ("ON", 1, ["night"]),
    ("BD", 2, ["morning", "night"]), ("TDS", 3, ["morning", "afternoon", "night"]),
    ("QID", 4, ["morning", "afternoon", "evening", "night"]),
])
def test_frequency_codes(code, times, when):
    rx = parse_line(f"Amlodipine 5mg {code}")
    assert rx.times_per_day == times
    assert rx.times_of_day == when


def test_before_food_and_as_needed():
    rx = parse_line("T. Gliclazide 80mg 1/1 OM AC")
    assert rx.timing == "before_food"
    prn = parse_line("Paracetamol 500mg PRN")
    assert prn.as_needed is True
    assert prn.times_per_day is None


def test_a_line_without_a_frequency_is_not_a_prescription():
    assert parse_line("Dx: T2DM, HTN") is None
    assert parse_line("TCA 2/52") is None


# Doctors often type shorthand in lower or mixed case. A code the parser did not recognise used to drop the
# whole medicine from the draft (so from the safety check and the patient summary), or silently lose
# "after food".
@pytest.mark.parametrize("line, times, when, timing", [
    ("Tab metformin 500mg bd pc", 2, ["morning", "night"], "after_food"),
    ("T. Metformin 500mg 1/1 Bd Pc", 2, ["morning", "night"], "after_food"),
    ("Tab metformin 500mg BD pc", 2, ["morning", "night"], "after_food"),
    ("Cap omeprazole 20mg od ac", 1, ["morning"], "before_food"),
    ("Tab simvastatin 20mg on", 1, ["night"], None),
    ("tab amlodipine 5mg tds", 3, ["morning", "afternoon", "night"], None),
])
def test_lower_and_mixed_case_codes_on_a_prescription_line(line, times, when, timing):
    rx = parse_line(line)
    assert rx is not None, line
    assert rx.times_per_day == times
    assert rx.times_of_day == when
    assert rx.timing == timing


def test_lower_case_as_needed():
    rx = parse_line("tab paracetamol 1g prn")
    assert rx.as_needed and rx.strength_mg == 1000


@pytest.mark.parametrize("sentence", [
    "Review on 12 Oct",
    "Patient is doing well on current meds",
    "Check her sugar on each visit",
    "Advised to eat before exercise, rest well",
])
def test_ordinary_sentences_with_code_like_words_are_not_prescriptions(sentence):
    # Lower-case codes only count on a line that is clearly a medicine order (a strength or a dosage form).
    assert parse_line(sentence) is None
