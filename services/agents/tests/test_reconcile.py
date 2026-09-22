from agents.evaluator import CurrentMed, PatientFacts, reconcile

KK = CurrentMed(name="Metformin 500mg", source="Klinik Kesihatan")
GP = CurrentMed(name="Brand A 500mg", source="GP clinic")


def checks(findings):
    return [f.check for f in findings]


def test_the_same_drug_from_two_places_is_a_duplicate_naming_both():
    findings = reconcile([KK, GP], herbs=[])
    assert checks(findings) == ["duplicate"]
    assert "Klinik Kesihatan" in findings[0].detail and "GP clinic" in findings[0].detail


def test_two_things_the_patient_already_takes_can_clash():
    findings = reconcile([CurrentMed(name="Warfarin 5mg", source="Hospital"), CurrentMed(name="Aspirin 100mg", source="Pharmacy")], herbs=[])
    assert checks(findings) == ["interaction"]
    assert findings[0].severity == "CRITICAL"


def test_a_herb_that_affects_a_medicine_already_taken_is_flagged():
    findings = reconcile([KK], herbs=["Jus peria (bitter gourd)"])
    assert checks(findings) == ["herb"]


def test_a_medicine_the_patient_is_allergic_to_is_flagged():
    findings = reconcile([CurrentMed(name="Amoxicillin 500mg", source="GP clinic")], herbs=[], patient=PatientFacts(allergies=["penicillin"]))
    assert checks(findings) == ["allergy"]


def test_names_not_in_the_drug_list_are_left_for_the_doctor_to_check():
    findings = reconcile([CurrentMed(name="Ubat kuning", source="Family")], herbs=[])
    assert checks(findings) == ["unrecognised"]


def test_a_tidy_list_has_no_findings():
    assert reconcile([KK, CurrentMed(name="Amlodipine 5mg", source="Klinik Kesihatan")], herbs=[]) == []
