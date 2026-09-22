"""
The pre-visit report: what the patient told the intake chat, laid out for the
doctor before the consultation. It is built from rules and the drug data, never
from the model's opinion, so everything in it can be traced to the patient's
own words. The doctor still confirms each point with the patient.
"""
import re
from typing import Optional

from pydantic import BaseModel, Field

from agents.evaluator import _data, generic_of
from agents.triage import classify_reply

# Checked in this order: the allergy question also mentions medicine.
TOPICS = (
    ("allergies", ("allerg", "alah", "alergi", "过敏", "ஒவ்வாமை")),
    ("medicines", ("take", "taking", "medicine", "ubat", "jamu", "supplement", "服用", "药", "மருந்து")),
    ("conditions", ("condition", "penyakit", "diabetes", "kencing manis", "darah tinggi", "病", "நோய்")),
    ("reason", ("brings you", "sebab", "today", "hari ini", "今天", "இன்று")),
)

# Remedies worth asking about when the patient does not name what is in them.
ASK_ABOUT = ("jamu", "herbal", "traditional", "tradisional", "ubat kampung", "supplement", "supplemen", "中药", "草药", "நாட்டு மருந்து")

_STRENGTH = r"(?:\s*\d+(?:\.\d+)?\s*(?:mg|mcg|g)\b)?"


class Answer(BaseModel):
    topic: str
    question: str
    answer: str


class MedicineMention(BaseModel):
    as_written: str
    generic: Optional[str] = None


class Flag(BaseModel):
    level: str
    matched: str


class PreVisitReport(BaseModel):
    reason: Optional[str] = None
    answers: list[Answer] = Field(default_factory=list)
    medicines: list[MedicineMention] = Field(default_factory=list)
    herbs: list[str] = Field(default_factory=list)
    allergies: list[str] = Field(default_factory=list)
    ask_about: list[str] = Field(default_factory=list)
    red_flags: list[Flag] = Field(default_factory=list)


def _topic(question: str) -> str:
    lowered = question.lower()
    for topic, words in TOPICS:
        if any(w in lowered for w in words):
            return topic
    return "other"


def _pattern(word: str, suffix: str = "") -> re.Pattern:
    return re.compile(rf"(?<![a-z]){re.escape(word)}{suffix}(?![a-z])", re.IGNORECASE)


def _pair(messages: list[dict]) -> list[Answer]:
    answers: list[Answer] = []
    question = ""
    for m in messages:
        role, content = m.get("role"), (m.get("content") or "").strip()
        if role == "assistant":
            question = content
        elif role == "user" and content:
            if answers and answers[-1].question == question and question:
                answers[-1].answer += " " + content
            else:
                answers.append(Answer(topic=_topic(question), question=question, answer=content))
    return answers


def _medicines(text: str) -> list[tuple[int, MedicineMention]]:
    data = _data()
    found = []
    for name in list(data["generics"]) + list(data["brands"]):
        for match in _pattern(name, _STRENGTH).finditer(text):
            found.append((match.start(), MedicineMention(as_written=match.group(0).strip(), generic=generic_of(match.group(0)))))
    return found


def _allergies(text: str) -> list[tuple[int, str]]:
    data = _data()
    names = set(data["generics"]) | {c for g in data["generics"].values() for c in g.get("classes", [])}
    found = [(m.start(), name) for name in names for m in [_pattern(name).search(text)] if m]
    for brand, generic in data["brands"].items():
        if (m := _pattern(brand).search(text)):
            found.append((m.start(), generic))
    return found


def _herbs(text: str) -> list[str]:
    found = []
    for rule in _data()["herbs"]:
        for name in rule["names"]:
            if _pattern(name).search(text):
                found.append(name)
                break
    return found


def _unique(items):
    seen, out = set(), []
    for item in items:
        key = item.lower() if isinstance(item, str) else item.as_written.lower()
        if key not in seen:
            seen.add(key)
            out.append(item)
    return out


def build_previsit_report(messages: list[dict]) -> PreVisitReport:
    answers = _pair(messages)
    medicines, herbs, allergies, ask_about, flags = [], [], [], [], []
    for a in answers:
        if a.topic == "allergies":
            allergies += [name for _, name in sorted(_allergies(a.answer))]
            continue
        medicines += [m for _, m in sorted(_medicines(a.answer), key=lambda pair: pair[0])]
        herbs += _herbs(a.answer)
        lowered = a.answer.lower()
        ask_about += [w for w in ASK_ABOUT if w in lowered]
    for a in answers:
        result = classify_reply(a.answer)
        if result.level in ("red", "watch"):
            flags.append(Flag(level=result.level, matched=result.matched))

    reason = next((a.answer for a in answers if a.topic == "reason"), answers[0].answer if answers else None)
    return PreVisitReport(
        reason=reason,
        answers=answers,
        medicines=_unique(medicines),
        herbs=_unique(herbs),
        allergies=_unique(allergies),
        ask_about=_unique(ask_about),
        red_flags=flags,
    )
