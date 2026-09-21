"""
Keyword triage for follow-up replies. This is the deterministic half of the
triage; an LLM check runs alongside it, and the clinic is alerted if either
one flags. It deliberately ignores negation ("tak pening" still alerts),
because a false alarm costs a phone call and a missed one costs much more.
"""
import json
import re
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Optional

WORDS_FILE = Path(__file__).resolve().parents[1] / "data" / "triage_words.json"
LEVELS = ("red", "watch", "ok")
_LATIN = re.compile(r"^[a-z' -]+$")


@dataclass(frozen=True)
class TriageResult:
    level: str  # red | watch | ok | review
    matched: Optional[str] = None


@lru_cache(maxsize=1)
def _rules() -> list[tuple[str, list[str]]]:
    data = json.loads(WORDS_FILE.read_text(encoding="utf-8"))
    return [(level, [w.lower() for words in data[level].values() for w in words]) for level in LEVELS]


def _contains(text: str, word: str) -> bool:
    if _LATIN.match(word):
        return re.search(rf"(?<![a-z]){re.escape(word)}(?![a-z])", text) is not None
    return word in text  # Chinese and Tamil have no spaces to rely on


def classify_reply(text: str) -> TriageResult:
    lowered = text.lower()
    for level, words in _rules():
        for word in words:
            if _contains(lowered, word):
                return TriageResult(level=level, matched=word)
    return TriageResult(level="review")
