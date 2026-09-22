"""The planted-error set: the checker must catch every planted mistake, and nothing in a clean draft."""
import json
from pathlib import Path

import pytest

from agents.evaluator import Draft, evaluate

SET = json.loads((Path(__file__).parent / "data" / "planted_errors.json").read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", SET["cases"], ids=[c["name"] for c in SET["cases"]])
def test_every_planted_error_is_caught(case):
    findings = evaluate(Draft(**case["draft"]))
    expected = case["expect"]
    assert any(f.check == expected["check"] and f.severity == expected["severity"] for f in findings), \
        f"{case['name']}: expected {expected}, got {[(f.check, f.severity) for f in findings]}"


def test_there_are_ten_planted_errors():
    assert len(SET["cases"]) == 10


def test_a_clean_draft_raises_nothing():
    assert evaluate(Draft(**SET["clean"])) == []
