"""
Evaluator: the safety checks run on every draft report before a doctor can
finalise it. Every check here uses data and rules, never the LLM's opinion.
(The hallucination check, which does need an LLM, is added separately.)
"""
import json
import re
from functools import lru_cache
from itertools import combinations
from pathlib import Path
from typing import Literal, Optional

from pydantic import BaseModel, Field

DATA_FILE = Path(__file__).resolve().parents[1] / "data" / "drugs.json"
REQUIRED_REPORT_FIELDS = ("diagnosis", "plan", "follow_up")

Severity = Literal["CRITICAL", "WARN"]


class Rx(BaseModel):
    name: str
    dose_mg: Optional[float] = None
    times_per_day: Optional[int] = None


class CurrentMed(BaseModel):
    name: str
    source: str = "unknown"


class PatientFacts(BaseModel):
    allergies: list[str] = Field(default_factory=list)
    pregnant: bool = False


class Draft(BaseModel):
    patient: PatientFacts = Field(default_factory=PatientFacts)
    prescription: list[Rx] = Field(default_factory=list)
    current_meds: list[CurrentMed] = Field(default_factory=list)
    herbs: list[str] = Field(default_factory=list)
    report: dict[str, str] = Field(default_factory=dict)


class Finding(BaseModel):
    check: str
    severity: Severity
    detail: str


@lru_cache(maxsize=1)
def _data() -> dict:
    return json.loads(DATA_FILE.read_text(encoding="utf-8"))


_DOSE = re.compile(r"\b\d+(?:\.\d+)?\s*(?:mg|mcg|g|ml|iu)\b")
_FORM = re.compile(r"\b(?:t\.|tab|tabs|tablet|tablets|cap|caps|capsule|capsules)\b\.?")


def _normalise(name: str) -> str:
    text = _DOSE.sub(" ", name.lower())
    text = _FORM.sub(" ", text)
    return re.sub(r"\s+", " ", text).strip(" .")


def generic_of(name: str) -> Optional[str]:
    """Map a brand or generic name, as written on a packet or prescription, to its generic."""
    text = _normalise(name)
    data = _data()
    if text in data["brands"]:
        return data["brands"][text]
    for generic in data["generics"]:
        if re.search(rf"\b{re.escape(generic)}\b", text):
            return generic
    return None


def _classes(generic: str) -> list[str]:
    return _data()["generics"][generic].get("classes", [])


def _check_allergies(draft: Draft, prescribed: dict[str, Rx]) -> list[Finding]:
    allergies = {a.strip().lower() for a in draft.patient.allergies}
    found = []
    for generic in prescribed:
        hits = allergies & ({generic} | set(_classes(generic)))
        if hits:
            found.append(Finding(check="allergy", severity="CRITICAL",
                                 detail=f"{generic.title()} prescribed, but the patient is allergic to {', '.join(sorted(hits))}."))
    return found


def _check_duplicates(prescribed: dict[str, Rx], current: dict[str, CurrentMed]) -> list[Finding]:
    return [
        Finding(check="duplicate", severity="CRITICAL",
                detail=f"{generic.title()} is already taken as '{current[generic].name}' from {current[generic].source}.")
        for generic in prescribed if generic in current
    ]


def _check_interactions(prescribed: dict[str, Rx], current: dict[str, CurrentMed]) -> list[Finding]:
    all_drugs = set(prescribed) | set(current)
    found = []
    for rule in _data()["interactions"]:
        a, b = rule["drugs"]
        involves_new = a in prescribed or b in prescribed
        if a in all_drugs and b in all_drugs and involves_new:
            severity = "CRITICAL" if rule["severity"] == "major" else "WARN"
            found.append(Finding(check="interaction", severity=severity, detail=f"{a.title()} + {b.title()}: {rule['effect']}"))
    return found


def _check_herbs(draft: Draft, prescribed: dict[str, Rx]) -> list[Finding]:
    found = []
    taken = [h.lower() for h in draft.herbs]
    for rule in _data()["herbs"]:
        if not any(n in h for h in taken for n in rule["names"]):
            continue
        for generic in prescribed:
            if generic in rule.get("affects_generics", []) or set(_classes(generic)) & set(rule.get("affects_classes", [])):
                found.append(Finding(check="herb", severity="WARN", detail=f"{rule['names'][0].title()} with {generic.title()}: {rule['effect']}"))
    return found


def _check_doses(prescribed: dict[str, Rx]) -> list[Finding]:
    found = []
    for generic, rx in prescribed.items():
        limit = _data()["generics"][generic].get("max_daily_mg")
        if limit is None or rx.dose_mg is None:
            continue
        daily = rx.dose_mg * (rx.times_per_day or 1)
        if daily > limit:
            found.append(Finding(check="dose", severity="CRITICAL",
                                 detail=f"{generic.title()} {daily:g} mg a day is above the {limit:g} mg maximum."))
    return found


def _check_pregnancy(draft: Draft, prescribed: dict[str, Rx]) -> list[Finding]:
    if not draft.patient.pregnant:
        return []
    return [
        Finding(check="pregnancy", severity="CRITICAL", detail=f"{generic.title()} should be avoided in pregnancy.")
        for generic in prescribed if _data()["generics"][generic].get("pregnancy_avoid")
    ]


def _check_completeness(draft: Draft) -> list[Finding]:
    missing = [f for f in REQUIRED_REPORT_FIELDS if not draft.report.get(f, "").strip()]
    if not missing:
        return []
    return [Finding(check="completeness", severity="WARN", detail=f"Report is missing: {', '.join(missing)}.")]


def evaluate(draft: Draft) -> list[Finding]:
    """Run every data-based safety check. CRITICAL findings come first."""
    findings: list[Finding] = []
    prescribed: dict[str, Rx] = {}
    for rx in draft.prescription:
        generic = generic_of(rx.name)
        if generic is None:
            findings.append(Finding(check="unrecognised", severity="WARN", detail=f"'{rx.name}' is not in the drug list; check it by hand."))
        else:
            prescribed[generic] = rx
    current = {g: m for m in draft.current_meds if (g := generic_of(m.name))}

    findings += _check_allergies(draft, prescribed)
    findings += _check_duplicates(prescribed, current)
    findings += _check_interactions(prescribed, current)
    findings += _check_herbs(draft, prescribed)
    findings += _check_doses(prescribed)
    findings += _check_pregnancy(draft, prescribed)
    findings += _check_completeness(draft)
    return sorted(findings, key=lambda f: f.severity != "CRITICAL")
