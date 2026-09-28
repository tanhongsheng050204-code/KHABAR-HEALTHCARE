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


# Dictated notes arrive as prose ("Tablet metformin 500 mg, twice daily, after meals."). These fixed
# rules rewrite the common spoken forms into the shorthand below, so dictation is structured by the
# same deterministic parser as typing. No model reads doses or frequencies.
_NUMBER_WORDS = {"half": 0.5, "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6,
                 "seven": 7, "eight": 8, "nine": 9, "ten": 10, "eleven": 11, "twelve": 12}
_NUMBER = r"(\d+(?:\.\d+)?|" + "|".join(_NUMBER_WORDS) + ")"
_ABBREVIATIONS = {"t", "tab", "tabs", "cap", "caps", "syr", "inj", "dr", "mr", "mrs", "ms", "pt", "no", "vs"}
_SENTENCE_END = re.compile(r"[.!?]\s+")
# "Diagnosis" may be said without a pause ("Diagnosis diabetes"); "Plan" needs one, or "Plan to ..."
# would be taken as a label.
_SPOKEN_LABEL = re.compile(r"^\s*(?:(diagnosis|impression)\b\s*[,:\-]?|(plan)\s*[,:\-])\s*", re.IGNORECASE)
# Speech without pauses comes back as one sentence with commas; a comma followed by the start of a
# new item ("..., Tablet ...", "..., review in 2 weeks") separates items.
_ITEM_AFTER_COMMA = re.compile(
    r",\s+(?=T\.\s|(?:tablets?|capsules?|syrup|injection|tabs?|caps?|review|follow[- ]?up|come back|"
    r"see (?:you|me) again|reviewed|return|rtc|tca|plan|diagnosis|impression|dx)\b)", re.IGNORECASE)
_FORM_START = re.compile(r"^\s*(?:T\.|Tabs?\b|Caps?\b|Syr\b|Inj\b|tablets?\b|capsules?\b|syrup\b|injection\b)", re.IGNORECASE)
_SPOKEN_FORMS = [(re.compile(r"^\s*(?:tablets?|tabs?)\b\.?", re.IGNORECASE), "T."),
                 (re.compile(r"^\s*(?:capsules?|caps?)\b\.?", re.IGNORECASE), "Cap."),
                 (re.compile(r"^\s*syrup\b\.?", re.IGNORECASE), "Syr."),
                 (re.compile(r"^\s*injection\b\.?", re.IGNORECASE), "Inj.")]
_SPOKEN_STRENGTH = [(re.compile(r"(\d+(?:\.\d+)?)\s*(?:milligrams?|mgs?)\b", re.IGNORECASE), r"\1mg"),
                    (re.compile(r"(\d+(?:\.\d+)?)\s*(?:micrograms?|mcg)\b", re.IGNORECASE), r"\1mcg"),
                    (re.compile(r"(\d+(?:\.\d+)?)\s*grams?\b", re.IGNORECASE), r"\1g")]
_SPOKEN_UNITS = re.compile(rf"\b{_NUMBER}\s+(?:tablets?|capsules?|tabs?|caps?)\b", re.IGNORECASE)
# Longest phrases first, so "twice daily" becomes BD before "daily" could become OD.
_SPOKEN_CODES = [(r"four times (?:a|per) day|four times daily", "QID"),
                 (r"three times (?:a|per) day|three times daily|thrice (?:a day|daily)", "TDS"),
                 (r"twice (?:a|per) day|twice daily|two times (?:a|per) day|two times daily", "BD"),
                 (r"once (?:a|per) day|once daily|every day|daily", "OD"),
                 (r"(?:at|every) night|nightly|at bedtime|before bed", "ON"),
                 (r"every morning|in the morning", "OM"),
                 (r"(?:as|when|if) (?:needed|necessary|required)", "PRN"),
                 (r"after (?:meals?|food|eating)", "PC"),
                 (r"before (?:meals?|food|eating)|on an empty stomach", "AC")]
_SPOKEN_CODES = [(re.compile(rf"\b(?:{p})\b", re.IGNORECASE), code) for p, code in _SPOKEN_CODES]
_STRENGTH_PRESENT = re.compile(r"\d\s*(?:mg|mcg|g|milligrams?|micrograms?|grams?)\b", re.IGNORECASE)
_SPOKEN_FORM_WORD = re.compile(r"^\s*(?:tablets?|capsules?|syrup|injection)\b", re.IGNORECASE)
_SPOKEN_UNIT_WORD = re.compile(r"\b(?:milligrams?|micrograms?|grams?)\b", re.IGNORECASE)


def _is_spoken_order(sentence: str) -> bool:
    """A medicine order said aloud. Typed shorthand ("Metformin 500 mg BD") is left exactly as written."""
    looks_like_order = _SPOKEN_FORM_WORD.match(sentence) or _STRENGTH_PRESENT.search(sentence)
    spoken = (_SPOKEN_FORM_WORD.match(sentence) or _SPOKEN_UNIT_WORD.search(sentence)
              or _SPOKEN_UNITS.search(sentence) or any(p.search(sentence) for p, _ in _SPOKEN_CODES))
    return bool(looks_like_order and spoken)
_SPOKEN_FOLLOW_UP = re.compile(
    rf"^\s*(?:review(?:ed)?|follow(?:ed)?[- ]?up|come back|see (?:you|me) again)\b.*?\b(?:in|after)\s+{_NUMBER}\s+(weeks?|days?)\b",
    re.IGNORECASE)
_SPOKEN_RETURN = re.compile(r"^\s*return\s+(?:to\s+(?:the\s+)?clinic\s+)?if\b", re.IGNORECASE)


def _number(word: str) -> float:
    return _NUMBER_WORDS.get(word.lower()) or float(word)


def _sentences(line: str) -> list[str]:
    """Splits at sentence ends, but not after abbreviations such as "T." or "Dr."."""
    parts, start = [], 0
    for end in _SENTENCE_END.finditer(line):
        words = line[start:end.start()].split()
        last = words[-1].lower() if words else ""
        if len(last) <= 1 or last in _ABBREVIATIONS:
            continue
        parts.append(line[start:end.start() + 1].strip())
        start = end.end()
    parts.append(line[start:].strip())
    return [p for p in parts if p]


def _from_speech(sentence: str) -> str:
    """Rewrites one spoken sentence into shorthand; labelled lines keep the doctor's own words."""
    label = _SPOKEN_LABEL.match(sentence)
    if label:
        return ("Plan: " if label.group(2) else "Dx: ") + sentence[label.end():]
    follow_up = _SPOKEN_FOLLOW_UP.match(sentence)
    if follow_up:
        per = 52 if follow_up.group(2).lower().startswith("week") else 7
        return f"Review {_number(follow_up.group(1)):g}/{per}" + sentence[follow_up.end():]
    if _SPOKEN_RETURN.match(sentence):
        return "RTC if" + sentence[_SPOKEN_RETURN.match(sentence).end():]
    if _is_spoken_order(sentence):
        for pattern, form in _SPOKEN_FORMS:
            sentence = pattern.sub(form, sentence, count=1)
        for pattern, unit in _SPOKEN_STRENGTH:
            sentence = pattern.sub(unit, sentence)
        sentence = _SPOKEN_UNITS.sub(lambda m: f"{_number(m.group(1)):g}/1", sentence)
        for pattern, code in _SPOKEN_CODES:
            sentence = pattern.sub(code, sentence)
    return sentence


def _labelled(sentence: str) -> bool:
    return any(p.match(sentence) for p in _LABELS.values())


def _starts_item(sentence: str, inside_label: bool, new_sentence: bool) -> bool:
    """Whether a piece begins a new part of the note rather than continuing the previous one. After a
    label only a new label, a follow-up, a medicine order, or a new sentence of return advice does,
    so the doctor's own sentences stay in their Dx:/Plan: line."""
    if _labelled(sentence) or _FOLLOW_UP.match(sentence) or _FORM_START.match(sentence):
        return True
    if inside_label:
        return new_sentence and bool(_RETURN_ADVICE.match(sentence))
    return bool(_RETURN_ADVICE.search(sentence) or parse_line(sentence))


def _chunks(line: str) -> list[tuple[str, str]]:
    """(text, separator before it): sentences, and comma-separated pieces that start a new item."""
    out = []
    for i, sentence in enumerate(_sentences(line)):
        for j, part in enumerate(_ITEM_AFTER_COMMA.split(sentence)):
            out.append((part.strip(), ", " if j else " "))
    return [(text, sep) for text, sep in out if text]


def _structured_lines(notes: str) -> list[str]:
    """One item per line. Items are grouped from the doctor's original words, then each whole item
    is rewritten once, so a continuation is never converted apart from the order it belongs to."""
    items = []
    for line in notes.splitlines():
        current, inside_label = None, False
        for text, sep in _chunks(line):
            spoken = _from_speech(text)
            if current is not None and not _starts_item(spoken, inside_label, sep == " "):
                current += sep + text
                continue
            if current is not None:
                items.append(current)
            current, inside_label = text, _labelled(spoken)
        if current is not None:
            items.append(current)
    return [_from_speech(item) for item in items]


def draft_from_notes(notes: str) -> ReportDraft:
    draft = ReportDraft()
    for line in _structured_lines(notes):
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
