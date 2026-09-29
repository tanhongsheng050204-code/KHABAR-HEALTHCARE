"""Writes the words the landing film shows, from the real agents code, so the page never says something the
product would not: web/components/film/product-words.json. Run from services/agents/:
    .venv/Scripts/python.exe -m scripts.make_film_words
tests/test_film_words.py fails when this output would change."""
import json
from pathlib import Path

from agents.evaluator import Draft, Rx, evaluate
from agents.prescription import parse_line
from agents.summary import build_summary

OUTPUT = Path(__file__).resolve().parents[3] / "web" / "components" / "film" / "product-words.json"
LANGS = ("ms", "en", "zh", "ta")

# Act 1: what the doctor writes, as doctors really type it (lower case included).
SHORTHAND = ["Tab metformin 500mg bd pc", "Tab amlodipine 5mg od", "Tab paracetamol 1g prn"]

# Act 2: the doctor's notes, with one slip of a zero (5000 mg instead of 500 mg) for the safety check to catch.
NOTES = "Dx: T2DM, HTN\nTab metformin 5000mg bd pc\nTab amlodipine 5mg od\nReview in 4 weeks"


def film_words() -> dict:
    rx_lines = []
    for line in SHORTHAND:
        rx = parse_line(line)
        rx_lines.append({
            "shorthand": line,
            "medicine": build_summary([rx], "en").medicines[0].medicine,
            "how": {lang: build_summary([rx], lang).medicines[0].how for lang in LANGS},
        })

    parsed = [rx for rx in (parse_line(line) for line in NOTES.splitlines()) if rx]
    draft = Draft(
        prescription=[Rx(name=rx.name, dose_mg=rx.dose_mg, times_per_day=rx.times_per_day) for rx in parsed],
        report={"diagnosis": "T2DM, HTN", "plan": "Continue treatment", "follow_up": "Review in 4 weeks"},
    )
    critical = next(f for f in evaluate(draft) if f.severity == "CRITICAL")
    return {
        "rx": rx_lines,
        "visit": {
            "notes": NOTES,
            "draft": [
                {"name": rx.name, "strengthMg": rx.strength_mg, "timesPerDay": rx.times_per_day, "timing": rx.timing}
                for rx in parsed
            ],
            "finding": {"severity": critical.severity, "detail": critical.detail},
        },
    }


if __name__ == "__main__":
    OUTPUT.write_text(json.dumps(film_words(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUTPUT}")
