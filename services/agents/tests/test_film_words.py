"""The landing film shows the product's own words. This fails when the agents code would now say something
different from web/components/film/product-words.json; regenerate it with scripts/make_film_words.py."""
import json

from scripts.make_film_words import OUTPUT, film_words


def test_the_film_shows_exactly_what_the_agents_produce():
    assert OUTPUT.exists(), "run: .venv/Scripts/python.exe -m scripts.make_film_words"
    assert json.loads(OUTPUT.read_text(encoding="utf-8")) == film_words()


def test_the_planted_error_is_caught_as_critical():
    finding = film_words()["visit"]["finding"]
    assert finding["severity"] == "CRITICAL"
    assert "maximum" in finding["detail"]


def test_every_summary_line_reads_after_food_when_the_shorthand_says_pc():
    metformin = film_words()["rx"][0]
    assert metformin["shorthand"].lower().endswith("pc")
    assert metformin["how"]["en"].endswith("after food.")
