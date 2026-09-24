"""
Triage for follow-up replies, in two halves. The word lists are deterministic;
an optional model reads the reply as well, and the more urgent of the two wins,
so the model can raise a reply but never lower one. The word lists deliberately
ignore negation ("tak pening" still alerts), because a false alarm costs a
phone call and a missed one costs much more.
"""
import json
import logging
import re
from dataclasses import dataclass, replace
from functools import lru_cache
from pathlib import Path
from typing import Any, Literal, Optional

from pydantic import BaseModel, Field

from core.config import settings
from core.deid import scrub

WORDS_FILE = Path(__file__).resolve().parents[1] / "data" / "triage_words.json"
LEVELS = ("red", "watch", "ok")
URGENCY = {"ok": 0, "review": 1, "watch": 2, "red": 3}
_LATIN = re.compile(r"^[a-z' -]+$")

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class TriageResult:
    level: str  # red | watch | ok | review
    matched: Optional[str] = None
    source: str = "keywords"  # keywords | model
    reason: Optional[str] = None
    missed_dose: bool = False  # the patient says they skipped or forgot a dose


class TriageVerdict(BaseModel):
    """What the triage model must return."""
    level: Literal["red", "watch", "review", "ok"] = Field(description="red, watch, review or ok")
    reason: str = Field(default="", description="One short sentence on why")


MODEL_PROMPT = """You triage a patient's reply to a follow-up check-in from a Malaysian clinic.
The reply may be in Malay, English, Chinese, Tamil or a mix. Choose one level:
- red: may need urgent care today (chest pain or tightness, breathing trouble, fainting,
  stroke signs, bleeding, confusion, signs of very low or very high blood sugar).
- watch: a new or worsening symptom or a medicine side effect the doctor should know about.
- ok: the patient says they are well and taking their medicine.
- review: you cannot tell.
When unsure between two levels, choose the more urgent one. Never give medical advice."""


@lru_cache(maxsize=1)
def _rules() -> list[tuple[str, list[str]]]:
    data = json.loads(WORDS_FILE.read_text(encoding="utf-8"))
    return [(level, [w.lower() for words in data[level].values() for w in words]) for level in LEVELS]


@lru_cache(maxsize=1)
def _missed_dose_words() -> list[str]:
    data = json.loads(WORDS_FILE.read_text(encoding="utf-8"))
    return [w.lower() for words in data["missed_dose"].values() for w in words]


def _contains(text: str, word: str) -> bool:
    if _LATIN.match(word):
        return re.search(rf"(?<![a-z]){re.escape(word)}(?![a-z])", text) is not None
    return word in text  # Chinese and Tamil have no spaces to rely on


# A blood sugar the patient typed, e.g. "gula 2.8", "sugar was 18.2", "血糖只有2.8". Readings over 35 are
# taken as mg/dL. Levels match the API's home readings: below 3.0 red, below 3.9 or above 16.7 watch.
_SUGAR = re.compile(r"(?:gula|sugar|glucose|血糖|சர்க்கரை)\D{0,20}?(\d{1,3}(?:[.,]\d+)?)", re.IGNORECASE)


def _sugar_level(text: str) -> Optional[TriageResult]:
    worst = None
    for m in _SUGAR.finditer(text):
        value = float(m.group(1).replace(",", "."))
        mmol = value / 18 if value > 35 else value
        level = "red" if mmol < 3.0 else "watch" if mmol < 3.9 or mmol > 16.7 else None
        if level and (worst is None or URGENCY[level] > URGENCY[worst.level]):
            worst = TriageResult(level=level, matched=m.group(0).strip())
    return worst


def classify_reply(text: str) -> TriageResult:
    lowered = text.lower()
    missed = any(_contains(lowered, w) for w in _missed_dose_words())
    words = TriageResult(level="review", missed_dose=missed)
    for level, listed in _rules():
        match = next((w for w in listed if _contains(lowered, w)), None)
        if match:
            words = TriageResult(level=level, matched=match, missed_dose=missed)
            break
    sugar = _sugar_level(lowered)
    if sugar and URGENCY[sugar.level] > URGENCY[words.level]:
        return replace(sugar, missed_dose=missed)
    return words


def _about_patient(context: Optional[dict]) -> str:
    """What the patient graph knows, for the model: a sweaty diabetic on gliclazide is not the same as anyone sweating."""
    if not context:
        return ""
    lines = []
    if context.get("conditions"):
        lines.append("Conditions: " + ", ".join(context["conditions"]))
    taken = [m["name"] for m in context.get("medicines", [])] + [h["name"] for h in context.get("herbs", [])]
    if taken:
        lines.append("Takes: " + ", ".join(taken))
    if context.get("allergies"):
        lines.append("Allergies: " + ", ".join(context["allergies"]))
    return "\n\nWhat the clinic knows about this patient:\n" + "\n".join(lines) if lines else ""


def triage(text: str, model: Optional[Any] = None, context: Optional[dict] = None) -> TriageResult:
    """Word lists first, then the model if one is configured. The more urgent level wins."""
    keywords = classify_reply(text)
    if model is None:
        return keywords
    from langchain_core.messages import HumanMessage, SystemMessage

    try:
        verdict = model.invoke([SystemMessage(content=MODEL_PROMPT + _about_patient(context)), HumanMessage(content=scrub(text))])
    except Exception as e:  # the word lists alone still protect the patient
        log.warning("Triage model failed, using the word lists only: %s", e)
        return keywords
    if URGENCY[verdict.level] > URGENCY[keywords.level]:
        return replace(keywords, level=verdict.level, source="model", reason=verdict.reason or None)
    return keywords


def default_model():
    """Gemini with structured output when a key is configured, otherwise None (word lists only)."""
    if not settings.GEMINI_API_KEY:
        return None
    from langchain_google_genai import ChatGoogleGenerativeAI

    model = ChatGoogleGenerativeAI(model=settings.GEMINI_MODEL, google_api_key=settings.GEMINI_API_KEY, temperature=0)
    return model.with_structured_output(TriageVerdict)
