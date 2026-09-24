"""
The transcription test from plan.md: five Manglish recordings of fictional consultation notes, scored on
what matters clinically. For each recording put the audio and a reference transcript side by side:

    evals/recordings/01.m4a   evals/recordings/01.txt   (the words actually spoken, typed by you)

    python -m scripts.score_transcripts                                   # transcribe with Groq, then score
    python -m scripts.score_transcripts --models whisper-large-v3-turbo whisper-large-v3
    python -m scripts.score_transcripts --score-only                      # score existing *.<model>.txt files

Needs GROQ_API_KEY unless --score-only. Recordings of real patients must never be used. The drug-name
count is the result that decides: a medicine missed or heard as another medicine is a safety problem,
while other word errors are an inconvenience the doctor fixes while reviewing.
"""
import argparse
import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from agents.evaluator import _data  # noqa: E402

RECORDINGS = ROOT / "evals" / "recordings"
AUDIO = (".m4a", ".mp3", ".wav", ".webm", ".ogg")
_WORD = re.compile(r"[a-z0-9]+(?:\.[0-9]+)?")
_DOSE = re.compile(r"(\d+(?:\.\d+)?)\s*(mg|g|mcg|ml|iu)\b")


def _names() -> list[str]:
    data = _data()
    return sorted(set(data["generics"]) | set(data["brands"]), key=len, reverse=True)


def _mentions(text: str) -> set[str]:
    lowered = text.lower()
    return {name for name in _names() if re.search(rf"(?<![a-z]){re.escape(name)}(?![a-z])", lowered)}


def drug_name_errors(reference: str, heard: str) -> dict:
    """Medicines said but not heard (missed), and medicines heard but never said (wrong)."""
    said, got = _mentions(reference), _mentions(heard)
    return {"missed": sorted(said - got), "wrong": sorted(got - said)}


def dose_errors(reference: str, heard: str) -> list[str]:
    said = [f"{n}{u}" for n, u in _DOSE.findall(reference.lower())]
    got = [f"{n}{u}" for n, u in _DOSE.findall(heard.lower())]
    return [d for d in said if d not in got]


def word_error_rate(reference: str, heard: str) -> float:
    ref, hyp = _WORD.findall(reference.lower()), _WORD.findall(heard.lower())
    if not ref:
        return 0.0
    row = list(range(len(hyp) + 1))
    for i, r in enumerate(ref, 1):
        prev, row[0] = row[0], i
        for j, h in enumerate(hyp, 1):
            prev, row[j] = row[j], min(row[j] + 1, row[j - 1] + 1, prev + (r != h))
    return row[-1] / len(ref)


def transcribe_all(model: str) -> None:
    from agents import transcribe as transcriber

    transcriber.MODEL = model
    for audio in sorted(p for p in RECORDINGS.iterdir() if p.suffix.lower() in AUDIO):
        out = audio.with_suffix(f".{model}.txt")
        text = transcriber.transcribe(audio.read_bytes(), audio.name)
        out.write_text(text + "\n", encoding="utf-8")
        print(f"  {audio.name} -> {out.name}")
        time.sleep(1)


def report(model: str) -> str:
    rows, missed, wrong, doses = [], 0, 0, 0
    for reference in sorted(RECORDINGS.glob("[0-9]*.txt")):
        if reference.stem.count(".") or reference.name.endswith(f".{model}.txt"):
            continue
        heard_file = reference.with_name(f"{reference.stem}.{model}.txt")
        if not heard_file.exists():
            continue
        said, heard = reference.read_text(encoding="utf-8"), heard_file.read_text(encoding="utf-8")
        names, dose = drug_name_errors(said, heard), dose_errors(said, heard)
        missed, wrong, doses = missed + len(names["missed"]), wrong + len(names["wrong"]), doses + len(dose)
        rows.append(f"| {reference.stem} | {word_error_rate(said, heard):.0%} | {', '.join(names['missed']) or '-'} "
                    f"| {', '.join(names['wrong']) or '-'} | {', '.join(dose) or '-'} |")
    if not rows:
        return f"## {model}\n\nNo transcripts to score yet.\n"
    return "\n".join([f"## {model}: {missed} medicines missed, {wrong} heard wrongly, {doses} doses lost", "",
                      "| Recording | Word error rate | Medicines missed | Medicines heard wrongly | Doses lost |",
                      "|---|---|---|---|---|", *rows]) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--models", nargs="*", default=["whisper-large-v3-turbo"])
    parser.add_argument("--score-only", action="store_true", help="score transcripts already saved, without calling Groq")
    parser.add_argument("--out", help="also write the report to this Markdown file")
    args = parser.parse_args()
    if not RECORDINGS.exists():
        print(f"Put recordings and reference transcripts in {RECORDINGS} first.", file=sys.stderr)
        return 2
    if not args.score_only:
        from core.config import settings

        if not settings.GROQ_API_KEY:
            print("GROQ_API_KEY is not set. Put it in services/agents/.env (never commit it), or use --score-only.", file=sys.stderr)
            return 2
        for model in args.models:
            transcribe_all(model)
    text = f"# Transcription test ({time.strftime('%d %b %Y')})\n\n" + "\n".join(report(m) for m in args.models)
    print(text)
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
