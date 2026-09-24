"""
Measures follow-up triage on the labelled fictional replies in evals/triage_cases.json, and compares
candidate models for the optional second read.

    python -m scripts.compare_triage_models                               # the word lists alone
    python -m scripts.compare_triage_models --models gemini-3.6-flash gemini-3.5-flash-lite
    python -m scripts.compare_triage_models --set holdout                 # replies never used for tuning

Models are read with GEMINI_API_KEY (from the environment or .env). Each model is scored the way it runs
in production: word lists and model together, the more urgent level winning. The model's own answer is
scored as well, to show what it adds. The number that matters most is red replies not raised to red:
each one is a patient who should have been called today.
"""
import argparse
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from agents.triage import MODEL_PROMPT, URGENCY, TriageVerdict, classify_reply, triage  # noqa: E402
from core.config import settings  # noqa: E402
from core.deid import scrub  # noqa: E402

SETS = {"cases": ROOT / "evals" / "triage_cases.json", "holdout": ROOT / "evals" / "triage_holdout.json"}
CASES: list[dict] = []


def score(levels: list[str]) -> dict:
    """levels[i] is the level given to CASES[i]."""
    red_missed = [c["text"] for c, got in zip(CASES, levels) if c["expect"] == "red" and got != "red"]
    under = [c["text"] for c, got in zip(CASES, levels) if URGENCY[got] < URGENCY[c["expect"]]]
    false_alarms = [c["text"] for c, got in zip(CASES, levels) if c["expect"] == "ok" and URGENCY[got] >= URGENCY["watch"]]
    reds = sum(c["expect"] == "red" for c in CASES)
    return {"red_caught": reds - len(red_missed), "reds": reds, "red_missed": red_missed,
            "under_triaged": under, "false_alarms": false_alarms}


def model_for(name: str):
    from langchain_google_genai import ChatGoogleGenerativeAI

    return ChatGoogleGenerativeAI(model=name, google_api_key=settings.GEMINI_API_KEY, temperature=0) \
        .with_structured_output(TriageVerdict)


def run_model(name: str) -> tuple[list[str], list[str], int]:
    """(combined levels, the model's own levels, errors). A failed call counts as 'review' for the model alone."""
    from langchain_core.messages import HumanMessage, SystemMessage

    model = model_for(name)
    combined, alone, errors = [], [], 0
    for case in CASES:
        try:
            verdict = model.invoke([SystemMessage(content=MODEL_PROMPT), HumanMessage(content=scrub(case["text"]))])
            alone.append(verdict.level)
        except Exception as e:  # noqa: BLE001 - counted and reported, never hidden
            errors += 1
            alone.append("review")
            print(f"  {name}: call failed on {case['text']!r}: {e}", file=sys.stderr)
        combined.append(triage(case["text"], model=_Fixed(alone[-1])).level)
        time.sleep(0.5)  # stay inside free-tier rate limits
    return combined, alone, errors


class _Fixed:
    """Replays the verdict already fetched, so the production triage() combines it without a second call."""

    def __init__(self, level: str):
        self.level = level

    def invoke(self, _messages):
        return TriageVerdict(level=self.level, reason="")


def table(rows: list[tuple[str, dict, str]]) -> str:
    out = ["| Triage | Red caught | Under-triaged | False alarms on 'ok' | Notes |", "|---|---|---|---|---|"]
    for label, s, note in rows:
        out.append(f"| {label} | {s['red_caught']}/{s['reds']} | {len(s['under_triaged'])} | {len(s['false_alarms'])} | {note} |")
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--models", nargs="*", default=[], help="Gemini model names to compare")
    parser.add_argument("--set", choices=SETS, default="cases", help="which labelled replies to use")
    parser.add_argument("--out", help="also write the report to this Markdown file")
    args = parser.parse_args()
    CASES[:] = json.loads(SETS[args.set].read_text(encoding="utf-8"))["cases"]

    keywords = score([classify_reply(c["text"]).level for c in CASES])
    rows = [("Word lists alone", keywords, "no model, no network")]
    details = [("Word lists alone", keywords)]
    if args.models and not settings.GEMINI_API_KEY:
        print("GEMINI_API_KEY is not set, so only the word lists were measured.", file=sys.stderr)
        args.models = []
    for name in args.models:
        combined, alone, errors = run_model(name)
        rows.append((f"Word lists + {name}", score(combined), f"{errors} failed calls" if errors else ""))
        rows.append((f"{name} alone", score(alone), "for comparison only; never used alone"))
        details.append((f"Word lists + {name}", score(combined)))

    report = [f"# Triage comparison: {args.set} ({len(CASES)} fictional replies, {time.strftime('%d %b %Y')})", "", table(rows), ""]
    for label, s in details:
        if s["red_missed"] or s["under_triaged"] or s["false_alarms"]:
            report.append(f"## {label}")
            report += [f"- red not raised: {t}" for t in s["red_missed"]]
            report += [f"- under-triaged: {t}" for t in s["under_triaged"] if t not in s["red_missed"]]
            report += [f"- false alarm: {t}" for t in s["false_alarms"]]
            report.append("")
    text = "\n".join(report)
    print(text)
    if args.out:
        Path(args.out).write_text(text + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
