import pytest

from agents.triage import classify_reply


@pytest.mark.parametrize("reply", ["sakit dada sejak pagi", "I have chest pain", "胸痛", "நெஞ்சு வலி", "sesak nafas"])
def test_emergency_symptoms_are_red_in_every_language(reply):
    assert classify_reply(reply).level == "red"


@pytest.mark.parametrize("reply", ["pening sikit", "rasa berpeluh", "feeling dizzy", "头晕", "மயக்கம்"])
def test_warning_symptoms_are_watch(reply):
    assert classify_reply(reply).level == "watch"


@pytest.mark.parametrize("reply", ["dah makan ubat, sihat", "fine thanks", "很好", "நலம்"])
def test_reassuring_replies_are_ok(reply):
    assert classify_reply(reply).level == "ok"


def test_red_wins_when_a_reply_mixes_reassurance_and_emergency():
    assert classify_reply("okay je, cuma sakit dada").level == "red"


def test_unrecognised_replies_go_to_a_person():
    assert classify_reply("hmm entah").level == "review"


def test_negated_symptoms_still_alert_because_triage_errs_towards_safety():
    assert classify_reply("tak pening pun").level == "watch"


def test_short_words_only_match_whole_words():
    # "ok" must not match inside "look"
    assert classify_reply("look at this").level == "review"


def test_result_names_the_word_that_matched():
    assert classify_reply("Pening dan berpeluh").matched == "pening"


@pytest.mark.parametrize("reply", ["Semalam lupa makan ubat", "I forgot to take it last night", "昨天忘记吃药了", "நேற்று மருந்து சாப்பிட மறந்துவிட்டேன்"])
def test_a_missed_dose_is_noticed_in_every_language(reply):
    assert classify_reply(reply).missed_dose is True


def test_taking_the_medicine_is_not_a_missed_dose():
    assert classify_reply("Dah makan ubat, sihat").missed_dose is False


def test_a_missed_dose_does_not_change_the_level():
    assert classify_reply("Semalam lupa makan ubat").level == "review"
    assert classify_reply("Lupa makan ubat, sekarang pening").level == "watch"


@pytest.mark.parametrize("reply,level", [
    ("Gula saya 2.8 tadi", "red"),
    ("sugar was 2.5 this morning", "red"),
    ("血糖只有2.8", "red"),
    ("glucose 45", "red"),          # mg/dL, read as 2.5 mmol/L
    ("Gula saya 3.5 pagi tadi", "watch"),
    ("sugar reading 18.2 after dinner", "watch"),
    ("gula 6.1, rasa sihat", "ok"),  # a normal number leaves the words to decide
])
def test_a_typed_blood_sugar_is_judged_like_a_home_reading(reply, level):
    assert classify_reply(reply).level == level


def test_a_low_sugar_outranks_a_reassuring_word():
    result = classify_reply("okay je, sugar 2.9")
    assert result.level == "red"
    assert "2.9" in result.matched


def test_every_red_reply_in_the_labelled_set_is_raised():
    """A regression guard only: the word lists were widened against these cases. See evals/triage_holdout.json
    and docs/evals for replies they were not tuned on, which they mostly miss without a model."""
    import json
    from pathlib import Path

    cases = json.loads((Path(__file__).resolve().parents[1] / "evals" / "triage_cases.json").read_text(encoding="utf-8"))["cases"]
    missed = [c["text"] for c in cases if c["expect"] == "red" and classify_reply(c["text"]).level != "red"]
    assert missed == []
