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


def run_with(graph, messages, context, language="English"):
    return graph.invoke({
        "graph_id": "g-123",
        "preferred_language": language,
        "messages": messages,
        "context": context,
        "next_question": "",
        "is_complete": False,
    })


KNOWN = {"medicines": ["Metformin 500mg", "Amlodipine 5mg"], "allergies": ["penicillin"], "last_diagnosis": "T2DM"}
TWO_ANSWERS = [{"role": "user", "content": "Pening"}, {"role": "user", "content": "Kencing manis"}]
THREE_ANSWERS = TWO_ANSWERS + [{"role": "user", "content": "Sama"}]


def test_the_scripted_intake_asks_to_confirm_the_medicines_already_on_record():
    result = run_with(build_intake_graph(llm=None), TWO_ANSWERS, KNOWN)
    question = result["next_question"]
    assert "Metformin 500mg" in question and "Amlodipine 5mg" in question
    assert "jamu" in question.lower()


def test_the_scripted_intake_asks_to_confirm_known_allergies_in_bm():
    result = run_with(build_intake_graph(llm=None), THREE_ANSWERS, KNOWN, language="BM")
    assert "penicillin" in result["next_question"] and "alahan" in result["next_question"].lower()


def test_with_nothing_on_record_the_questions_are_the_usual_ones():
    result = run_with(build_intake_graph(llm=None), TWO_ANSWERS, {})
    assert result["next_question"].startswith("What do you take at the moment?")


def test_the_model_is_told_what_the_clinic_already_knows():
    llm = RecordingLLM()
    run_with(build_intake_graph(llm=llm), [{"role": "user", "content": "Pening"}], KNOWN)
    system = llm.sent[0].content
    assert "Metformin 500mg" in system and "penicillin" in system and "T2DM" in system


GRAPH = {
    "conditions": ["diabetes", "hypertension"],
    "allergies": ["penicillin"],
    "medicines": [{"name": "Metformin 500mg", "generic": "metformin", "source": "Klinik Kesihatan"},
                  {"name": "Brand A 500mg", "generic": "metformin", "source": "GP clinic"}],
    "herbs": [{"name": "Jus peria (bitter gourd)", "herb": "bitter gourd", "source": "Her sister"}],
    "last_visit": None, "recent_symptoms": [], "recent_readings": [], "pregnant": False,
}


def test_the_intake_looks_up_the_patient_graph_by_graph_id_when_the_clinic_sent_nothing():
    asked = []
    graph = build_intake_graph(llm=None, graph_context=lambda gid: asked.append(gid) or GRAPH)
    result = run_with(graph, TWO_ANSWERS, {})
    assert asked == ["g-123"]
    assert "Brand A 500mg" in result["next_question"] and "Jus peria (bitter gourd)" in result["next_question"]


def test_what_the_clinic_sent_wins_over_the_graph():
    graph = build_intake_graph(llm=None, graph_context=lambda gid: GRAPH)
    result = run_with(graph, TWO_ANSWERS, {"medicines": ["Amlodipine 5mg"]})
    assert "Amlodipine 5mg" in result["next_question"] and "Brand A" not in result["next_question"]


def test_the_model_is_told_the_conditions_on_record_from_the_graph():
    llm = RecordingLLM()
    run_with(build_intake_graph(llm=llm, graph_context=lambda gid: GRAPH), [{"role": "user", "content": "Pening"}], {})
    system = llm.sent[0].content
    assert "Conditions on record: diabetes, hypertension" in system
    assert "g-123" not in system


def test_without_a_graph_the_intake_runs_as_before():
    result = run_with(build_intake_graph(llm=None, graph_context=lambda gid: None), TWO_ANSWERS, {})
    assert "jamu" in result["next_question"].lower()
