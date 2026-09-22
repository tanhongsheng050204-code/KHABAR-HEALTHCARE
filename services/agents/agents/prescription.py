"""
Parses one line of Malaysian clinic prescription shorthand, e.g. "T. Metformin 500mg 1/1 BD PC".
Deterministic on purpose: dosing must never depend on an AI's reading of the note.
"""
import re
from typing import Optional

from pydantic import BaseModel, computed_field

# frequency code -> (times per day, times of day)
FREQUENCIES = {
    "OD": (1, ["morning"]),
    "OM": (1, ["morning"]),
    "ON": (1, ["night"]),
    "HS": (1, ["night"]),
    "BD": (2, ["morning", "night"]),
    "TDS": (3, ["morning", "afternoon", "night"]),
    "TID": (3, ["morning", "afternoon", "night"]),
    "QID": (4, ["morning", "afternoon", "evening", "night"]),
    "QDS": (4, ["morning", "afternoon", "evening", "night"]),
    "STAT": (1, ["now"]),
}
TIMINGS = {"AC": "before_food", "PC": "after_food"}
FORMS = r"(?:T\.|Tab\.?|Tabs\.?|Cap\.?|Caps\.?|Syr\.?|Inj\.?)"

_STRENGTH = re.compile(r"(\d+(?:\.\d+)?)\s*(mg|mcg|g)\b", re.IGNORECASE)
_UNITS = re.compile(r"\b(\d+(?:\.\d+)?)/1\b")
_CODE = re.compile(r"\b(" + "|".join(sorted(FREQUENCIES, key=len, reverse=True)) + r"|PRN)\b")
_TIMING = re.compile(r"\b(AC|PC)\b")
_LEADING = re.compile(r"^\s*(?:\d+[.)]\s*)?(?:" + FORMS + r"\s*)?", re.IGNORECASE)


class ParsedRx(BaseModel):
    raw: str
    name: str
    strength_mg: Optional[float] = None
    units_per_dose: float = 1
    times_per_day: Optional[int] = None
    times_of_day: list[str] = []
    timing: Optional[str] = None  # before_food | after_food
    as_needed: bool = False

    @computed_field
    @property
    def dose_mg(self) -> Optional[float]:
        return None if self.strength_mg is None else self.strength_mg * self.units_per_dose


def _to_mg(value: float, unit: str) -> float:
    unit = unit.lower()
    return value * 1000 if unit == "g" else value / 1000 if unit == "mcg" else value


def parse_line(line: str) -> Optional[ParsedRx]:
    """Returns None when the line is not a prescription (no frequency code or PRN)."""
    codes = _CODE.findall(line)
    if not codes:
        return None
    strength = _STRENGTH.search(line)
    name_end = strength.start() if strength else _CODE.search(line).start()
    name = _LEADING.sub("", line[:name_end]).strip(" .-")
    if not name or not re.search(r"[A-Za-z]", name):
        return None

    rx = ParsedRx(raw=line.strip(), name=name)
    if strength:
        rx.strength_mg = _to_mg(float(strength.group(1)), strength.group(2))
    units = _UNITS.search(line)
    if units:
        rx.units_per_dose = float(units.group(1))
    frequency = next((c for c in codes if c in FREQUENCIES), None)
    if frequency:
        rx.times_per_day, rx.times_of_day = FREQUENCIES[frequency][0], list(FREQUENCIES[frequency][1])
    rx.as_needed = "PRN" in codes
    timing = _TIMING.search(line)
    if timing:
        rx.timing = TIMINGS[timing.group(1)]
    return rx
