"""
Report agent: turns the doctor's typed (or transcribed) notes into a structured draft report.
Sections the doctor did not write stay empty; nothing is invented. The prescription is always
read by the deterministic shorthand parser, never by an LLM.
"""
import re

from pydantic import BaseModel, Field

from agents.prescription import ParsedRx, parse_line

_LABELS = {
    "diagnosis": re.compile(r"^\s*(?:dx|diagnosis|imp|impression)\s*[:\-]\s*(.+)$", re.IGNORECASE),
    "plan": re.compile(r"^\s*(?:plan|mx|management)\s*[:\-]\s*(.+)$", re.IGNORECASE),
}
_FOLLOW_UP = re.compile(r"^\s*(?:TCA|review|follow[- ]?up)\b.*$", re.IGNORECASE)
_WEEKS = re.compile(r"\b(\d+)\s*/\s*52\b")
_DAYS = re.compile(r"\b(\d+)\s*/\s*7\b")
_RETURN_ADVICE = re.compile(r"\b(RTC|return to clinic|come back if)\b", re.IGNORECASE)


class ReportDraft(BaseModel):
    diagnosis: str = ""
    plan: str = ""
    follow_up: str = ""
    follow_up_weeks: float | None = None
    warning_signs: list[str] = Field(default_factory=list)
    prescription: list[ParsedRx] = Field(default_factory=list)


def draft_from_notes(notes: str) -> ReportDraft:
    draft = ReportDraft()
    for line in notes.splitlines():
        if not line.strip():
            continue
        labelled = False
        for field, pattern in _LABELS.items():
            m = pattern.match(line)
            if m and not getattr(draft, field):
                setattr(draft, field, m.group(1).strip())
                labelled = True
        if labelled:
            continue
        if _FOLLOW_UP.match(line) and not draft.follow_up:
            draft.follow_up = line.strip()
            weeks, days = _WEEKS.search(line), _DAYS.search(line)
            draft.follow_up_weeks = float(weeks.group(1)) if weeks else (float(days.group(1)) / 7 if days else None)
            continue
        if _RETURN_ADVICE.search(line):
            draft.warning_signs.append(line.strip())
            continue
        rx = parse_line(line)
        if rx:
            draft.prescription.append(rx)
    return draft
