"""Concept rules: a body place plus a sign, standalone red flags, and the everyday phrases they must not flag."""
import pytest

from agents.triage import classify_reply
from agents.triage_concepts import detect


@pytest.mark.parametrize("text", [
    "Dada terasa berat macam kena tekan",            # chest + heavy
    "my chest feels really tight since lunch",       # chest + tight
    "nafas jadi pendek bila naik tangga",            # breath + short
    "呼吸有点困难",                                     # breathing + difficulty
    "lips look blue",                                # blue lips
    "batuk keluar darah",                            # blood + cough
    "Mulut dia senget sikit",                        # face droop
    "suddenly weak on one side",                     # one-sided weakness
    "rasa macam hypo",                               # low sugar
    "trembling and sweating a lot",                  # autonomic cluster
    "நெஞ்சு அழுத்தமா இருக்கு",                         # chest + pressure (Tamil)
])
def test_red_flags_said_in_new_ways_are_red(text):
    hit = detect(text)
    assert hit is not None and hit.level == "red", text
    assert classify_reply(text).level == "red"


@pytest.mark.parametrize("text", [
    "Doktor cakap saya tak boleh makan gula banyak",   # 'cakap' and 'tak boleh' far apart in meaning
    "blood pressure pill makes me cough",              # blood pressure is not bleeding
    "今天血糖6.5，很方便",                                 # 血糖 is not bleeding
    "My blood test was fine and I'm well",
    "Saya sihat, ubat makan ikut jadual",
    "BP this morning 128/82",
])
def test_everyday_phrases_are_not_red(text):
    hit = detect(text)
    assert hit is None or hit.level != "red", (text, hit)


def test_watch_concepts_raise_a_reply_the_lists_would_leave_for_review():
    result = classify_reply("My ankles are puffy by evening")
    assert result.level == "watch"
    assert result.source == "concept"
    assert result.reason == "swelling"


def test_the_more_urgent_of_lists_and_concepts_wins():
    # The lists already say red; a watch concept must not lower it.
    assert classify_reply("sakit dada dan kaki bengkak").level == "red"


def test_pairs_must_be_near_each_other():
    far = "dada " + "x " * 40 + "berat"
    assert detect(far) is None or detect(far).concept != "chest pain or pressure"


def test_a_reply_too_long_to_check_cheaply_is_never_left_as_fine():
    hit = detect("saya sihat " * 1000)
    assert hit is not None and hit.level == "watch"
