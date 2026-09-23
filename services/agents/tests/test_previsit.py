from agents.intake_agent import SCRIPTED_QUESTIONS
from agents.previsit import build_previsit_report

MS = SCRIPTED_QUESTIONS["ms"]
EN = SCRIPTED_QUESTIONS["en"]


def chat(questions, answers):
    messages = []
    for q, a in zip(questions, answers):
        messages += [{"role": "assistant", "content": q}, {"role": "user", "content": a}]
    return messages


AMINAH = chat(MS, [
    "Pening sejak 3 hari, rasa berpeluh",
    "Kencing manis dan darah tinggi",
    "Metformin dari klinik kesihatan, Brand A 500mg dari GP, jus peria dari kakak, dan jamu",
    "Alah penicillin",
])


def test_each_answer_is_paired_with_its_question_and_topic():
    report = build_previsit_report(AMINAH)
    assert [a.topic for a in report.answers] == ["reason", "conditions", "medicines", "allergies"]
    assert report.answers[0].question == MS[0]
    assert report.reason == "Pening sejak 3 hari, rasa berpeluh"


def test_medicines_are_found_with_their_generic_even_under_a_brand_name():
    report = build_previsit_report(AMINAH)
    written = [(m.as_written, m.generic) for m in report.medicines]
    assert ("Metformin", "metformin") in written
    assert ("Brand A 500mg", "metformin") in written


def test_known_herbs_are_found_and_unnamed_remedies_are_flagged_for_the_doctor_to_ask_about():
    report = build_previsit_report(AMINAH)
    assert report.herbs == ["peria"]
    assert report.ask_about == ["jamu"]


def test_an_allergy_answer_gives_allergies_and_is_not_read_as_a_medicine_taken():
    report = build_previsit_report(chat(EN, ["Cough", "None", "Nothing", "I am allergic to amoxicillin and penicillin"]))
    assert report.allergies == ["amoxicillin", "penicillin"]
    assert report.medicines == []


def test_saying_no_allergies_gives_none():
    report = build_previsit_report(chat(EN, ["Cough", "None", "Nothing", "No"]))
    assert report.allergies == []


def test_warning_symptoms_the_patient_mentions_are_raised():
    report = build_previsit_report(AMINAH)
    assert [(f.level, f.matched) for f in report.red_flags] == [("watch", "pening")]


def test_an_emergency_symptom_is_raised_as_red():
    report = build_previsit_report(chat(EN, ["Chest pain since this morning"]))
    assert report.red_flags[0].level == "red"


def test_an_empty_chat_gives_an_empty_report():
    report = build_previsit_report([])
    assert report.reason is None
    assert report.answers == [] and report.medicines == [] and report.red_flags == []


def test_long_term_conditions_are_named_in_plain_english_whatever_language_they_were_given_in():
    assert build_previsit_report(AMINAH).conditions == ["diabetes", "hypertension"]
    zh = build_previsit_report(chat(EN, ["头晕", "我有糖尿病和高血压，还有心脏病"]))
    assert zh.conditions == ["diabetes", "hypertension", "heart disease"]
    ta = build_previsit_report(chat(EN, ["Tired", "நீரிழிவு நோய் உண்டு"]))
    assert ta.conditions == ["diabetes"]


def test_a_condition_mentioned_outside_the_conditions_answer_is_not_recorded():
    report = build_previsit_report(chat(EN, ["My sister has diabetes, I have a cough", "None"]))
    assert report.conditions == []


def test_a_condition_the_patient_says_they_do_not_have_is_not_recorded():
    report = build_previsit_report(chat(EN, ["Cough", "No diabetes, only high blood pressure"]))
    assert report.conditions == ["hypertension"]
    bm = build_previsit_report(chat(MS, ["Batuk", "Tak ada kencing manis"]))
    assert bm.conditions == []
