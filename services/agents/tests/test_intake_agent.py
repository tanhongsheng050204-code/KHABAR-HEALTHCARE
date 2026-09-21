from agents.intake_agent import IntakeTurn, build_intake_graph


class RecordingLLM:
    """Stands in for Gemini: records what it was sent, returns a fixed turn."""

    def __init__(self):
        self.sent = []

    def invoke(self, messages):
        self.sent = messages
        return IntakeTurn(next_question="Sejak bila rasa pening?", is_complete=False)


def run(graph, messages, language="BM"):
    return graph.invoke({
        "graph_id": "g-123",
        "preferred_language": language,
        "messages": messages,
        "next_question": "",
        "is_complete": False,
    })


def test_patient_identifiers_are_scrubbed_before_reaching_the_llm():
    llm = RecordingLLM()
    graph = build_intake_graph(llm=llm)

    run(graph, [{"role": "user", "content": "IC saya 590312-10-5566, telefon 012-345 6789. Saya pening."}])

    sent_text = " ".join(m.content for m in llm.sent)
    assert "590312-10-5566" not in sent_text
    assert "012-345 6789" not in sent_text
    assert "[IC]" in sent_text and "[PHONE]" in sent_text


def test_llm_turn_is_returned_to_the_caller():
    graph = build_intake_graph(llm=RecordingLLM())
    result = run(graph, [{"role": "user", "content": "Saya pening"}])
    assert result["next_question"] == "Sejak bila rasa pening?"
    assert result["is_complete"] is False


def test_without_an_llm_the_scripted_intake_opens_with_the_reason_for_the_visit():
    graph = build_intake_graph(llm=None)
    result = run(graph, [], language="English")
    assert "bring" in result["next_question"].lower() or "reason" in result["next_question"].lower()
    assert result["is_complete"] is False


def test_scripted_intake_asks_about_jamu_and_supplements():
    graph = build_intake_graph(llm=None)
    answers = [{"role": "user", "content": "Pening"}, {"role": "user", "content": "Kencing manis"}]
    result = run(graph, answers, language="English")
    assert "jamu" in result["next_question"].lower()


def test_scripted_intake_completes_after_all_four_topics_are_answered():
    graph = build_intake_graph(llm=None)
    answers = [{"role": "user", "content": a} for a in ["Pening", "Kencing manis", "Metformin", "Tiada alahan"]]
    result = run(graph, answers)
    assert result["is_complete"] is True


def test_scripted_intake_speaks_bm_when_the_patient_prefers_bm():
    graph = build_intake_graph(llm=None)
    result = run(graph, [], language="BM")
    assert "apa" in result["next_question"].lower()
