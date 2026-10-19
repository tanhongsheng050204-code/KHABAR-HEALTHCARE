from agents.gloss import Gloss, GlossOutput, gloss


class FakeModel:
    def __init__(self, english="", fail=False):
        self.english = english
        self.fail = fail
        self.sent = []

    def invoke(self, messages):
        self.sent = messages
        if self.fail:
            raise RuntimeError("down")
        return GlossOutput(english=self.english)


def test_without_a_model_there_is_no_gloss():
    assert gloss("Saya pening sejak pagi") == Gloss()


def test_the_model_translates_for_staff():
    result = gloss("Saya pening sejak pagi", model=FakeModel("I have been dizzy since this morning"))
    assert result.english == "I have been dizzy since this morning"
    assert result.source == "model"


def test_a_failing_model_gives_no_gloss():
    assert gloss("Saya pening", model=FakeModel(fail=True)).english is None


def test_an_unchanged_english_reply_needs_no_gloss():
    assert gloss("I am fine", model=FakeModel("I am fine")).english is None


def test_identifiers_are_scrubbed_before_the_model_sees_the_text():
    model = FakeModel("ok")
    gloss("Call me at 012-345 6789 please", model=model)
    assert "012-345 6789" not in model.sent[-1].content


def test_the_endpoint_answers_without_a_model(monkeypatch):
    import routers.followup
    monkeypatch.setattr(routers.followup, "_gloss_model", None)
    from fastapi.testclient import TestClient
    from main import app
    client = TestClient(app)
    response = client.post("/agents/followup/gloss", json={"text": "Saya pening"}, headers={"X-Internal-Service-Key": "dev-internal-secret"})
    assert response.status_code == 200
    assert response.json()["english"] is None
