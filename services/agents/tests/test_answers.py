from agents.answers import AnswerChoice, AnswerOption, match_answer

MISSED = AnswerOption(id="missed", title="Missed a dose", triggers=["lupa makan ubat", "forgot", "忘记吃药", "ubat"])
REFILL = AnswerOption(id="refill", title="Medicine running out", triggers=["ubat dah habis", "ran out"])
OPTIONS = [MISSED, REFILL]


class FakeModel:
    def __init__(self, answer_id=None, fail=False):
        self.answer_id = answer_id
        self.fail = fail
        self.sent = []

    def invoke(self, messages):
        self.sent = messages
        if self.fail:
            raise RuntimeError("model unavailable")
        return AnswerChoice(answer_id=self.answer_id)


def test_a_reply_that_uses_a_trigger_phrase_gets_that_answer():
    assert match_answer("Semalam saya lupa makan ubat malam", OPTIONS).answer_id == "missed"


def test_triggers_work_in_chinese_without_spaces():
    assert match_answer("昨天忘记吃药了", OPTIONS).answer_id == "missed"


def test_the_longest_matching_trigger_wins():
    assert match_answer("Ubat dah habis, nak ambil lagi", OPTIONS).answer_id == "refill"


def test_without_a_match_or_a_model_there_is_no_answer():
    result = match_answer("Boleh makan durian?", OPTIONS)
    assert result.answer_id is None


def test_the_model_may_pick_an_approved_answer_the_triggers_missed():
    result = match_answer("I did not take last night's tablet", OPTIONS, model=FakeModel("missed"))
    assert result.answer_id == "missed"
    assert result.source == "model"


def test_the_model_cannot_pick_an_answer_that_is_not_on_the_list():
    assert match_answer("hmm", OPTIONS, model=FakeModel("made-up")).answer_id is None


def test_when_the_model_fails_there_is_no_answer():
    assert match_answer("hmm", OPTIONS, model=FakeModel(fail=True)).answer_id is None


def test_the_reply_is_scrubbed_before_the_model_sees_it():
    model = FakeModel(None)
    match_answer("IC 590312-10-5566, tak ambil tablet semalam", [MISSED], model=model)
    sent = " ".join(m.content for m in model.sent)
    assert "tablet semalam" in sent and "590312-10-5566" not in sent
