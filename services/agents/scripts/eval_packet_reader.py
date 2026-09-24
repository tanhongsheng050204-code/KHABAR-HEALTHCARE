"""
Runs the packet reader on the fictional packets in evals/packets/ and scores what it read.

    python -m scripts.eval_packet_reader                                  # GEMINI_MODEL
    python -m scripts.eval_packet_reader --models gemini-3.6-flash gemini-3.5-flash-lite --out ../../docs/evals/packets.md

Needs GEMINI_API_KEY. A packet passes when every expected generic or herb is found, no other generic is
claimed, and a packet too blurred to read is reported as unreadable instead of guessed. Generics always
come from our own brand and drug tables, so an "invented" generic means the label text itself was misread.
"""
import argparse
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from agents.evaluator import herb_of  # noqa: E402
from agents.packet_reader import PacketReaderUnavailable, PacketReadResult, read_packet  # noqa: E402
from core.config import settings  # noqa: E402

PACKETS = ROOT / "evals" / "packets"


def score_packet(expected: dict, result: PacketReadResult) -> dict:
    generics = {c.generic_candidate for c in result.candidates if c.generic_candidate}
    herbs = {h for c in result.candidates if c.kind != "MEDICINE" for h in [herb_of(c.ingredient_text or c.brand)] if h}
    missing = sorted(set(expected["expect_generics"]) - generics) + sorted(set(expected["expect_herbs"]) - herbs)
    invented = sorted(generics - set(expected["expect_generics"]))
    said_unreadable = result.unreadable or not result.candidates
    if expected["unreadable"]:
        passed = said_unreadable and not generics
    else:
        passed = not missing and not invented
    return {"passed": passed, "missing": missing, "invented": invented, "said_unreadable": said_unreadable,
            "confidence": ",".join(c.confidence for c in result.candidates) or "-"}


def run(model: str) -> list[tuple[dict, dict]]:
    manifest = json.loads((PACKETS / "manifest.json").read_text(encoding="utf-8"))["packets"]
    rows = []
    for packet in manifest:
        image = (PACKETS / packet["file"]).read_bytes()
        try:
            result = read_packet(image, "image/png", model=model)
            rows.append((packet, score_packet(packet, result)))
        except PacketReaderUnavailable as e:
            rows.append((packet, {"passed": False, "missing": [], "invented": [], "said_unreadable": None,
                                  "confidence": "-", "error": str(e)}))
        time.sleep(1)  # stay inside free-tier rate limits
    return rows


def report(model: str, rows: list[tuple[dict, dict]]) -> str:
    passed = sum(s["passed"] for _, s in rows)
    out = [f"## {model}: {passed}/{len(rows)} packets read correctly", "",
           "| Packet | Result | Missing | Invented | Confidence | What it tests |", "|---|---|---|---|---|---|"]
    for packet, s in rows:
        result = "✅" if s["passed"] else ("⚠️ error: " + s["error"] if s.get("error") else "❌")
        out.append(f"| {packet['file']} | {result} | {', '.join(s['missing']) or '-'} | {', '.join(s['invented']) or '-'} "
                   f"| {s['confidence']} | {packet['note']} |")
    return "\n".join(out) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--models", nargs="*", default=[settings.GEMINI_MODEL])
    parser.add_argument("--out", help="also write the report to this Markdown file")
    args = parser.parse_args()
    if not settings.GEMINI_API_KEY:
        print("GEMINI_API_KEY is not set. Put it in services/agents/.env (never commit it) and run again.", file=sys.stderr)
        return 2
    text = f"# Packet reader on fictional packets ({time.strftime('%d %b %Y')})\n\n" + "\n".join(report(m, run(m)) for m in args.models)
    print(text)
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
