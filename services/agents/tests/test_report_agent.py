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
