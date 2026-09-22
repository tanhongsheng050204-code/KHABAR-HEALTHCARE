"""
Builds the patient's plain-language summary in BM, English, Chinese or Tamil from the parsed
prescription. It uses fixed, reviewed phrases, not an LLM, so a dose or time can never be
mistranslated. In fasting mode it only moves doses it can move safely; anything else is sent
back to the doctor instead of guessed.
"""
from typing import Optional

from pydantic import BaseModel, Field

from agents.prescription import ParsedRx

PHRASES = {
    "ms": {
        "units": lambda n: f"{n:g} biji",
        "single": {"morning": "setiap pagi", "afternoon": "setiap tengah hari", "evening": "setiap petang", "night": "setiap malam", "now": "sekarang"},
        "word": {"morning": "pagi", "afternoon": "tengah hari", "evening": "petang", "night": "malam", "sahur": "waktu sahur", "berbuka": "waktu berbuka"},
        "and": " dan ", "sep": ", ", "end": ".",
        "timing": {"after_food": "selepas makan", "before_food": "sebelum makan"},
        "prn": "bila perlu sahaja",
        "ask": "Tanya doktor cara makan ubat ini semasa berpuasa.",
        "warning": "Kalau rasa lebih teruk atau ada tanda yang membimbangkan, datang ke klinik segera.",
        "weeks": lambda n: f"Datang semula dalam {n:g} minggu.",
        "days": lambda n: f"Datang semula dalam {n:g} hari.",
    },
    "en": {
        "units": lambda n: f"{n:g} tablet" + ("" if n == 1 else "s"),
        "single": {"morning": "every morning", "afternoon": "every afternoon", "evening": "every evening", "night": "every night", "now": "now"},
        "word": {"morning": "morning", "afternoon": "afternoon", "evening": "evening", "night": "night", "sahur": "at sahur", "berbuka": "at berbuka"},
        "and": " and ", "sep": ", ", "end": ".",
        "timing": {"after_food": "after food", "before_food": "before food"},
        "prn": "only when needed",
        "ask": "Ask your doctor how to take this while fasting.",
        "warning": "If you feel worse or notice anything worrying, come back to the clinic straight away.",
        "weeks": lambda n: f"Come back in {n:g} week" + ("" if n == 1 else "s") + ".",
        "days": lambda n: f"Come back in {n:g} day" + ("" if n == 1 else "s") + ".",
    },
    "zh": {
        "units": lambda n: f"每次{n:g}粒",
        "single": {"morning": "每天早上", "afternoon": "每天中午", "evening": "每天傍晚", "night": "每天晚上", "now": "现在"},
        "word": {"morning": "早上", "afternoon": "中午", "evening": "傍晚", "night": "晚上", "sahur": "封斋前（Sahur）", "berbuka": "开斋时（Berbuka）"},
        "and": "和", "list_sep": "、", "sep": "，", "end": "。",
        "timing": {"after_food": "饭后服用", "before_food": "饭前服用"},
        "prn": "需要时才服用",
        "ask": "斋戒期间怎么服用，请问医生。",
        "warning": "如果感觉更不舒服或有担心的症状，请立即回诊所。",
        "weeks": lambda n: f"{n:g}个星期后回来复诊。",
        "days": lambda n: f"{n:g}天后回来复诊。",
    },
    "ta": {
        "units": lambda n: f"{n:g} மாத்திரை",
        "single": {"morning": "தினமும் காலையில்", "afternoon": "தினமும் மதியம்", "evening": "தினமும் மாலையில்", "night": "தினமும் இரவில்", "now": "இப்போது"},
        "word": {"morning": "காலை", "afternoon": "மதியம்", "evening": "மாலை", "night": "இரவு", "sahur": "சஹர் நேரத்தில்", "berbuka": "நோன்பு திறக்கும் நேரத்தில்"},
        "and": " மற்றும் ", "sep": ", ", "end": ".",
        "timing": {"after_food": "சாப்பிட்ட பிறகு", "before_food": "சாப்பிடுவதற்கு முன்"},
        "prn": "தேவைப்படும்போது மட்டும்",
        "ask": "நோன்பின் போது இதை எப்படி எடுத்துக்கொள்வது என்று மருத்துவரிடம் கேளுங்கள்.",
        "warning": "உடல்நிலை மோசமானால் அல்லது கவலையான அறிகுறிகள் இருந்தால் உடனே கிளினிக்கிற்கு வாருங்கள்.",
        "weeks": lambda n: f"{n:g} வாரங்களில் மீண்டும் வாருங்கள்.",
        "days": lambda n: f"{n:g} நாட்களில் மீண்டும் வாருங்கள்.",
    },
}

# Which normal dose times can move to the two meals of a fasting day.
FASTING_MOVES = {"morning": "sahur", "night": "berbuka", "now": "now"}


class MedicineLine(BaseModel):
    medicine: str
    how: str


class PatientSummary(BaseModel):
    language: str
    medicines: list[MedicineLine]
    warning: str
    next_visit: Optional[str] = None
    needs_doctor: list[str] = Field(default_factory=list)

    def as_text(self) -> str:
        lines = [f"• {m.medicine}: {m.how}" for m in self.medicines]
        lines.append(self.warning)
        if self.next_visit:
            lines.append(self.next_visit)
        return "\n".join(lines)


def _join(words: list[str], p: dict) -> str:
    if len(words) == 1:
        return words[0]
    return p.get("list_sep", p["sep"]).join(words[:-1]) + p["and"] + words[-1]


def _label(rx: ParsedRx) -> str:
    return f"{rx.name} {rx.strength_mg:g} mg" if rx.strength_mg is not None else rx.name


def _times(rx: ParsedRx, p: dict, fasting: bool) -> Optional[str]:
    """Returns the time phrase, '' for as-needed, or None when fasting needs the doctor."""
    if rx.as_needed or not rx.times_of_day:
        return ""
    slots = rx.times_of_day
    if fasting:
        if any(s not in FASTING_MOVES for s in slots):
            return None
        slots = [FASTING_MOVES[s] for s in slots]
        return _join([p["word"][s] if s != "now" else p["single"]["now"] for s in slots], p)
    if len(slots) == 1:
        return p["single"][slots[0]]
    return _join([p["word"][s] for s in slots], p)


def _how(rx: ParsedRx, p: dict, fasting: bool) -> Optional[str]:
    times = _times(rx, p, fasting)
    if times is None:
        return None
    parts = [p["units"](rx.units_per_dose)]
    if rx.as_needed:
        parts.append(p["prn"])
    elif times:
        parts.append(times)
    if rx.timing and not rx.as_needed:
        parts.append(p["timing"][rx.timing])
    return p["sep"].join(parts) + p["end"]


def build_summary(prescription: list[ParsedRx], language: str, follow_up_weeks: Optional[float] = None,
                  fasting: bool = False) -> PatientSummary:
    lang = language if language in PHRASES else "en"
    p = PHRASES[lang]
    medicines, needs_doctor = [], []
    for rx in prescription:
        how = _how(rx, p, fasting)
        if how is None:
            needs_doctor.append(_label(rx))
            how = p["ask"]
        medicines.append(MedicineLine(medicine=_label(rx), how=how))

    next_visit = None
    if follow_up_weeks:
        days = round(follow_up_weeks * 7)
        next_visit = p["days"](days) if days < 7 else p["weeks"](round(follow_up_weeks, 1))
    return PatientSummary(language=lang, medicines=medicines, warning=p["warning"], next_visit=next_visit, needs_doctor=needs_doctor)
