from agents.evaluator import CurrentMed, Draft, PatientFacts, Rx, evaluate

COMPLETE_REPORT = {"diagnosis": "T2DM, HTN", "plan": "Continue meds", "follow_up": "TCA 2/52"}


def draft(**overrides):
    base = dict(patient=PatientFacts(), prescription=[], current_meds=[], herbs=[], report=COMPLETE_REPORT)
    base.update(overrides)
    return Draft(**base)


def checks(findings, severity=None):
    return [f.check for f in findings if severity is None or f.severity == severity]


def test_clean_draft_has_no_findings():
    assert evaluate(draft(prescription=[Rx(name="Metformin", dose_mg=500, times_per_day=2)])) == []


def test_allergy_to_a_drug_class_blocks_a_drug_in_that_class():
    findings = evaluate(draft(patient=PatientFacts(allergies=["Penicillin"]), prescription=[Rx(name="Amoxicillin")]))
    assert checks(findings, "CRITICAL") == ["allergy"]


def test_allergy_to_the_exact_drug_is_critical():
    findings = evaluate(draft(patient=PatientFacts(allergies=["aspirin"]), prescription=[Rx(name="Aspirin", dose_mg=100)]))
    assert "allergy" in checks(findings, "CRITICAL")


def test_major_interaction_with_a_medicine_from_another_clinic_is_critical():
    findings = evaluate(draft(current_meds=[CurrentMed(name="Warfarin", source="Specialist clinic")], prescription=[Rx(name="Aspirin", dose_mg=100)]))
    assert "interaction" in checks(findings, "CRITICAL")


def test_moderate_interaction_is_a_warning():
    findings = evaluate(draft(current_meds=[CurrentMed(name="Amlodipine")], prescription=[Rx(name="Simvastatin", dose_mg=40)]))
    assert checks(findings) == ["interaction"]
    assert findings[0].severity == "WARN"


def test_same_medicine_under_two_brand_names_is_a_critical_duplicate():
    findings = evaluate(draft(current_meds=[CurrentMed(name="Brand A 500 mg", source="Klinik A")], prescription=[Rx(name="Brand B", dose_mg=500, times_per_day=2)]))
    assert checks(findings, "CRITICAL") == ["duplicate"]
    assert "metformin" in findings[0].detail.lower()


def test_a_duplicate_names_every_place_the_patient_already_gets_the_medicine():
    current = [CurrentMed(name="Metformin 500mg", source="Klinik Kesihatan"), CurrentMed(name="Brand A 500mg", source="GP clinic")]
    findings = evaluate(draft(current_meds=current, prescription=[Rx(name="Metformin", dose_mg=500, times_per_day=2)]))
    assert checks(findings, "CRITICAL") == ["duplicate"]
    assert "Klinik Kesihatan" in findings[0].detail and "GP clinic" in findings[0].detail


def test_herb_that_affects_a_prescribed_drug_is_flagged():
    findings = evaluate(draft(herbs=["Ginkgo capsules"], prescription=[Rx(name="Aspirin", dose_mg=100)]))
    assert checks(findings) == ["herb"]


def test_daily_dose_above_the_maximum_is_critical():
    findings = evaluate(draft(prescription=[Rx(name="Metformin", dose_mg=1000, times_per_day=4)]))
    assert checks(findings, "CRITICAL") == ["dose"]


def test_drug_to_avoid_in_pregnancy_is_critical_for_a_pregnant_patient():
    findings = evaluate(draft(patient=PatientFacts(pregnant=True), prescription=[Rx(name="Perindopril", dose_mg=4)]))
    assert checks(findings, "CRITICAL") == ["pregnancy"]


def test_missing_report_fields_are_a_warning():
    findings = evaluate(draft(report={"diagnosis": "URTI", "plan": "", "follow_up": ""}))
    assert checks(findings) == ["completeness"]
    assert findings[0].severity == "WARN"


def test_unrecognised_medicine_is_flagged_for_manual_checking():
    findings = evaluate(draft(prescription=[Rx(name="Mysterymycin")]))
    assert checks(findings) == ["unrecognised"]


def test_critical_findings_come_first():
    findings = evaluate(draft(
        report={"diagnosis": "", "plan": "x", "follow_up": "x"},
        patient=PatientFacts(allergies=["aspirin"]),
        prescription=[Rx(name="Aspirin", dose_mg=100)],
    ))
    assert [f.severity for f in findings] == ["CRITICAL", "WARN"]


def test_drug_in_the_report_but_not_in_the_doctors_notes_is_critical():
    findings = evaluate(draft(
        source_text="T. Metformin 500mg 1/1 BD PC",
        prescription=[Rx(name="Metformin", dose_mg=500, times_per_day=2), Rx(name="Amlodipine", dose_mg=5)],
    ))
    assert checks(findings, "CRITICAL") == ["grounding"]
    assert "amlodipine" in findings[0].detail.lower()


def test_grounding_is_skipped_when_no_notes_are_given():
    findings = evaluate(draft(prescription=[Rx(name="Amlodipine", dose_mg=5)]))
    assert "grounding" not in checks(findings)


def test_a_brand_written_in_the_notes_grounds_its_generic():
    findings = evaluate(draft(source_text="Brand A 500 mg BD", prescription=[Rx(name="Metformin", dose_mg=500, times_per_day=2)]))
    assert "grounding" not in checks(findings)


def test_a_symptom_in_the_report_that_the_notes_never_mention_is_flagged():
    report = {"diagnosis": "T2DM with chest pain", "plan": "Continue meds", "follow_up": "TCA 2/52"}
    findings = evaluate(draft(report=report, source_text="c/o giddiness 3/7\nDx: T2DM\nTCA 2/52"))
    assert checks(findings) == ["symptom_grounding"]
    assert findings[0].severity == "WARN"
    assert "chest pain" in findings[0].detail


def test_a_symptom_written_another_way_in_the_notes_is_not_flagged():
    report = {"diagnosis": "Dizziness, likely postural", "plan": "Hydrate", "follow_up": "PRN"}
    findings = evaluate(draft(report=report, source_text="c/o giddiness on standing, pening sejak semalam"))
    assert "symptom_grounding" not in checks(findings)


def test_shortness_of_breath_matches_the_usual_abbreviation():
    report = {"diagnosis": "Shortness of breath, ?asthma", "plan": "Inhaler", "follow_up": "TCA 1/52"}
    findings = evaluate(draft(report=report, source_text="SOB on exertion x 2/7"))
    assert "symptom_grounding" not in checks(findings)


def test_the_graph_adds_what_the_request_left_out_without_repeating_it():
    from agents.evaluator import CurrentMed, Draft, PatientFacts, with_graph
    draft = Draft(patient=PatientFacts(allergies=["aspirin"]), current_meds=[CurrentMed(name="Metformin 500mg", source="KK")])
    context = {"pregnant": True, "allergies": ["penicillin", "aspirin"],
               "medicines": [{"name": "metformin 500mg", "generic": "metformin", "source": "KK"},
                             {"name": "Brand A 500mg", "generic": "metformin", "source": "GP clinic"}],
               "herbs": [{"name": "Jus peria (bitter gourd)", "herb": "bitter gourd", "source": "Family"}]}
    merged = with_graph(draft, context)
    assert merged.patient.allergies == ["aspirin", "penicillin"]
    assert merged.patient.pregnant is True
    assert [m.name for m in merged.current_meds] == ["Metformin 500mg", "Brand A 500mg"]
    assert merged.herbs == ["Jus peria (bitter gourd)"]


def test_without_graph_context_the_draft_is_unchanged():
    from agents.evaluator import Draft, with_graph
    draft = Draft(herbs=["peria"])
    assert with_graph(draft, None) == draft


def test_the_check_endpoint_reads_the_graph_when_given_a_graph_id(monkeypatch):
    from fastapi.testclient import TestClient
    from main import app
    from routers import evaluator as router
    monkeypatch.setattr(router.graph, "context_or_none", lambda gid: {"allergies": ["penicillin"]} if gid == "g-1" else None)
    body = {"graph_id": "g-1", "prescription": [{"name": "Amoxicillin", "dose_mg": 500, "times_per_day": 3}],
            "report": {"diagnosis": "Tonsillitis", "plan": "Antibiotics", "follow_up": "PRN"}}
    response = TestClient(app).post("/agents/evaluator/check", json=body, headers={"X-Internal-Service-Key": "dev-internal-secret"})
    assert response.json()["findings"][0]["check"] == "allergy"
