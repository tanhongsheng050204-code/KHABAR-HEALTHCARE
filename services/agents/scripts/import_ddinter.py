"""Build the small DDInter 2.0 subset used by the fake-patient safety demo.

Usage: python scripts/import_ddinter.py (from services/agents)

The official CSVs are downloaded to a temporary directory and are not retained.
Only interactions between generics listed in data/drugs.json are written.
"""

from __future__ import annotations

import csv
import argparse
import hashlib
import json
import tempfile
from contextlib import nullcontext
from pathlib import Path
from urllib.request import Request, urlopen


DATA_DIR = Path(__file__).resolve().parents[1] / "data"
DRUGS_FILE = DATA_DIR / "drugs.json"
OUTPUT_FILE = DATA_DIR / "ddinter_interactions.json"
SOURCE_BASE = "https://ddinter.scbdd.com/static/media/download"
SOURCE_CODES = ("A", "B", "D", "H", "L", "P", "R", "V")
LICENSE = "CC BY-NC-SA 4.0"


def _download(code: str, destination: Path) -> str:
    filename = f"ddinter_downloads_code_{code}.csv"
    request = Request(f"{SOURCE_BASE}/{filename}", headers={"User-Agent": "Khabar-DDInter-subset/1.0"})
    with urlopen(request, timeout=60) as response, destination.open("wb") as output:
        digest = hashlib.sha256()
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
            digest.update(chunk)
    return digest.hexdigest()


def build_subset(source_dir: Path | None = None) -> dict:
    drugs = json.loads(DRUGS_FILE.read_text(encoding="utf-8"))["generics"]
    ddinter_names = {
        info["ddinter_name"].casefold(): generic
        for generic, info in drugs.items()
        if info.get("ddinter_name")
    }
    ranks = {"major": 3, "moderate": 2, "minor": 1, "unknown": 0}
    pairs: dict[tuple[str, str], dict] = {}
    hashes: dict[str, str] = {}

    temporary_context = tempfile.TemporaryDirectory(prefix="khabar-ddinter-") if source_dir is None else nullcontext(str(source_dir))
    with temporary_context as temporary:
        directory = Path(temporary)
        for code in SOURCE_CODES:
            filename = f"ddinter_downloads_code_{code}.csv"
            path = directory / filename
            hashes[filename] = _download(code, path) if source_dir is None else hashlib.sha256(path.read_bytes()).hexdigest()
            with path.open(encoding="utf-8-sig", newline="") as source:
                for row in csv.DictReader(source):
                    left = ddinter_names.get(row["Drug_A"].strip().casefold())
                    right = ddinter_names.get(row["Drug_B"].strip().casefold())
                    if not left or not right or left == right:
                        continue
                    pair = tuple(sorted((left, right)))
                    level = row["Level"].strip().casefold()
                    current = pairs.get(pair)
                    record = {
                        "drugs": list(pair),
                        "level": level if level in ranks else "unknown",
                        "ddinter_ids": sorted((row["DDInterID_A"].strip(), row["DDInterID_B"].strip())),
                        "ddinter_names": sorted((row["Drug_A"].strip(), row["Drug_B"].strip()), key=str.casefold),
                    }
                    if current is None or ranks[record["level"]] > ranks[current["level"]]:
                        pairs[pair] = record

    interactions = sorted(pairs.values(), key=lambda item: item["drugs"])
    coverage = {
        generic: sum(generic in item["drugs"] for item in interactions)
        for generic in sorted(drugs)
    }
    return {
        "source": "DDInter 2.0",
        "source_url": "https://ddinter.scbdd.com/download/",
        "license": LICENSE,
        "source_files_sha256": hashes,
        "notice": "A missing pair is not evidence that the combination is safe. Gliclazide has no pair record in this downloaded subset.",
        "interactions": interactions,
        "coverage_pairs_per_generic": coverage,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, help="Use the official CSV files already in this directory")
    args = parser.parse_args()
    subset = build_subset(args.source_dir)
    OUTPUT_FILE.write_text(json.dumps(subset, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(subset['interactions'])} interactions to {OUTPUT_FILE}")
    print("Generics with records:", ", ".join(name for name, count in subset["coverage_pairs_per_generic"].items() if count))
    print("Generics without DDInter pair records:", ", ".join(name for name, count in subset["coverage_pairs_per_generic"].items() if not count))


if __name__ == "__main__":
    main()
