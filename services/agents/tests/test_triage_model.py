from agents.triage import TriageVerdict, triage


class FakeModel:
    """Stands in for Gemini: records what it was sent, returns a fixed verdict or fails."""

    def __init__(self, level="ok", fail=False):
        self.level = level
        self.fail = fail
        self.sent = []

    def invoke(self, messages):
        self.sent = messages
        if self.fail:
            raise RuntimeError("model unavailable")
        return TriageVerdict(level=self.level, reason="fake reason")


def test_the_model_can_raise_a_reply_the_word_lists_missed():
    result = triage("dada rasa ketat macam ada benda berat", model=FakeModel("red"))
    assert result.level == "red"
    assert result.source == "model"
    assert result.reason == "fake reason"


def test_the_model_cannot_lower_a_red_flag_from_the_word_lists():
    result = triage("sakit dada", model=FakeModel("ok"))
    assert result.level == "red"
    assert result.source == "keywords"


def test_the_model_cannot_clear_a_reply_nobody_recognised():
    assert triage("hmm entah", model=FakeModel("ok")).level == "review"


def test_when_the_model_fails_the_word_lists_still_decide():
    result = triage("pening", model=FakeModel(fail=True))
    assert result.level == "watch"
    assert result.source == "keywords"


def test_without_a_model_only_the_word_lists_run():
    result = triage("pening", model=None)
    assert result.level == "watch"
    assert result.matched == "pening"


def test_the_reply_is_scrubbed_before_the_model_sees_it():
    model = FakeModel("ok")
    triage("IC saya 590312-10-5566, call 012-345 6789", model=model)
    sent = " ".join(m.content for m in model.sent)
    assert "590312-10-5566" not in sent
    assert "012-345 6789" not in sent


def test_the_model_is_told_what_the_graph_knows_about_the_patient():
    model = FakeModel("ok")
    context = {"conditions": ["diabetes"], "medicines": [{"name": "Gliclazide 80mg", "generic": "gliclazide", "source": "GP"}],
               "allergies": [], "herbs": [{"name": "Jus peria", "herb": "bitter gourd", "source": "Family"}]}
    triage("berpeluh sikit", model=model, context=context)
    system = model.sent[0].content
    assert "diabetes" in system and "Gliclazide 80mg" in system and "Jus peria" in system


def test_without_context_the_model_prompt_is_unchanged():
    model = FakeModel("ok")
    triage("berpeluh sikit", model=model)
    assert "What the clinic knows" not in model.sent[0].content
