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
