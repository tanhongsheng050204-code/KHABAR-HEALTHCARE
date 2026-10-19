import json

import httpx
from fastapi.testclient import TestClient

from agents.copilot import AskOutput, CitedPatient, ScriptLine, ScriptOutput, ask, call_script
from agents.meal_reader import MealReaderUnavailable, read_meal

ROWS = [{"code": "P1", "pathways": ["DIABETES"], "risk": 6, "glucose": "WORSENING"},
        {"code": "P2", "pathways": ["HYPERTENSION"], "risk": 0}]


class FakeModel:
    def __init__(self, out=None, fail=False):
        self.out, self.fail, self.sent = out, fail, None

    def invoke(self, messages):
        self.sent = messages
        if self.fail:
            raise RuntimeError("down")
        return self.out


def test_without_a_model_ask_gives_nothing():
    assert ask("who is slipping?", ROWS).answer is None


def test_ask_keeps_only_codes_that_are_in_the_table():
    model = FakeModel(AskOutput(answer="P1 is slipping.", patients=[CitedPatient(code="P1", why="sugar worsening"),
                                                                    CitedPatient(code="P9", why="invented")]))
    result = ask("who is slipping?", ROWS, model=model)
    assert result.source == "model"
    assert [p.code for p in result.patients] == ["P1"]


def test_ask_scrubs_identifiers_from_the_question():
    model = FakeModel(AskOutput(answer="ok"))
    ask("what about 590312-10-5566 and 012-345 6789?", ROWS, model=model)
    sent = model.sent[-1].content
    assert "590312" not in sent and "6789" not in sent


def test_a_failing_model_falls_back_to_nothing():
    assert ask("q", ROWS, model=FakeModel(fail=True)).answer is None


def test_call_script_returns_bilingual_lines():
    model = FakeModel(ScriptOutput(lines=[ScriptLine(english="How are you?", spoken="Apa khabar?")]))
    result = call_script("ms", {"concerns": ["missed doses"]}, model=model)
    assert result.lines[0].spoken == "Apa khabar?"
    assert call_script("ms", {"concerns": ["x"]}).lines == []


def test_meal_reader_keeps_only_the_fixed_groups():
    def handler(request):
        body = {"candidates": [{"content": {"parts": [{"text": json.dumps({"categories": ["rice_noodles", "SWEET_DRINK", "POISON"]})}]}}]}
        return httpx.Response(200, json=body)

    result = read_meal(b"\xff\xd8\xff", "image/jpeg", api_key="k", client=httpx.Client(transport=httpx.MockTransport(handler)))
    assert result.categories == ["RICE_NOODLES", "SWEET_DRINK"]


def test_meal_reader_needs_a_key():
    try:
        read_meal(b"x", "image/jpeg", api_key="")
        assert False
    except MealReaderUnavailable:
        pass


def test_meal_endpoint_needs_consent():
    from main import app
    client = TestClient(app)
    r = client.post("/agents/meal/read?mime_type=image/jpeg", content=b"x", headers={"X-Internal-Service-Key": "dev-internal-secret"})
    assert r.status_code == 400
