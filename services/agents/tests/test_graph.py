import re

import neo4j
from fastapi.testclient import TestClient

from core import graph
from core.graph import PatientGraphReader
from main import app

client = TestClient(app)
KEY = {"X-Internal-Service-Key": "dev-internal-secret"}
GRAPH_ID = "7d1c2a4e-0000-4000-8000-00000000abcd"


class FakeTx:
    def __init__(self, rows_by_query):
        self.rows_by_query = rows_by_query
        self.calls = []

    def run(self, query, **params):
        self.calls.append((query, params))
        return self.rows_by_query.get(query, [])


class FakeSession:
    def __init__(self, tx):
        self.tx = tx

    def execute_read(self, work):
        return work(self.tx)

    def execute_write(self, work):
        raise AssertionError("The agents must never write to the patient graph")

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class FakeDriver:
    def __init__(self, rows_by_query):
        self.tx = FakeTx(rows_by_query)
        self.session_kwargs = []

    def session(self, **kwargs):
        self.session_kwargs.append(kwargs)
        return FakeSession(self.tx)


AMINAH_ROWS = {
    graph.PATIENT: [{"pregnant": False}],
    graph.CONDITIONS: [{"name": "diabetes"}, {"name": "hypertension"}],
    graph.ALLERGIES: [],
    graph.MEDICINES: [
        {"name": "Metformin 500mg", "generic": "metformin", "recognised": True, "source": "Klinik Kesihatan"},
        {"name": "Brand A 500mg", "generic": "metformin", "recognised": True, "source": "GP clinic"},
        {"name": "Ubat X", "generic": "ubat x", "recognised": False, "source": "Pharmacy"},
    ],
    graph.HERBS: [{"name": "Jus peria (bitter gourd)", "herb": "bitter gourd", "source": "Her sister"}],
    graph.LAST_VISIT: [{"at": "2026-09-20T02:00:00Z", "prescribed": ["gliclazide"]}],
    graph.SYMPTOMS: [{"word": "pening", "level": "watch", "at": "2026-09-22T03:00:00Z"}],
    graph.READINGS: [{"kind": "GLUCOSE", "value": "3.2 mmol/L", "level": "RED", "at": "2026-09-22T04:00:00Z"}],
}


def test_the_graph_is_read_in_a_read_only_session():
    driver = FakeDriver(AMINAH_ROWS)
    PatientGraphReader(driver).context(GRAPH_ID)
    assert driver.session_kwargs == [{"default_access_mode": neo4j.READ_ACCESS}]


def test_no_query_can_change_the_graph():
    for query in graph.READ_QUERIES:
        assert not re.search(r"\b(CREATE|MERGE|SET|DELETE|REMOVE|DETACH|LOAD|CALL)\b", query, re.IGNORECASE), query


def test_the_patient_is_looked_up_only_by_the_random_graph_id():
    driver = FakeDriver(AMINAH_ROWS)
    PatientGraphReader(driver).context(GRAPH_ID)
    assert driver.tx.calls
    assert all(params == {"graph_id": GRAPH_ID} for _, params in driver.tx.calls)


def test_the_context_gathers_what_the_graph_knows():
    context = PatientGraphReader(FakeDriver(AMINAH_ROWS)).context(GRAPH_ID)
    assert context["conditions"] == ["diabetes", "hypertension"]
    assert context["allergies"] == []
    assert context["pregnant"] is False
    assert context["medicines"][1] == {"name": "Brand A 500mg", "generic": "metformin", "source": "GP clinic"}
    assert context["medicines"][2]["generic"] is None
    assert context["herbs"] == [{"name": "Jus peria (bitter gourd)", "herb": "bitter gourd", "source": "Her sister"}]
    assert context["last_visit"] == {"at": "2026-09-20T02:00:00Z", "prescribed": ["gliclazide"]}
    assert context["recent_symptoms"][0]["word"] == "pening"
    assert context["recent_readings"][0]["level"] == "RED"


def test_a_patient_the_graph_has_never_seen_has_no_context():
    assert PatientGraphReader(FakeDriver({})).context(GRAPH_ID) is None


def test_without_a_neo4j_address_there_is_no_graph(monkeypatch):
    monkeypatch.setattr(graph.settings, "NEO4J_URI", "")
    graph.reset_reader()
    assert graph.reader() is None


def test_the_context_endpoint_serves_the_graph(monkeypatch):
    monkeypatch.setattr(graph, "reader", lambda: PatientGraphReader(FakeDriver(AMINAH_ROWS)))
    response = client.get(f"/agents/graph/{GRAPH_ID}/context", headers=KEY)
    assert response.status_code == 200
    assert response.json()["conditions"] == ["diabetes", "hypertension"]


def test_the_context_endpoint_needs_the_service_key():
    assert client.get(f"/agents/graph/{GRAPH_ID}/context").status_code == 401


def test_the_context_endpoint_says_when_there_is_no_graph(monkeypatch):
    monkeypatch.setattr(graph, "reader", lambda: None)
    assert client.get(f"/agents/graph/{GRAPH_ID}/context", headers=KEY).status_code == 503


def test_the_context_endpoint_says_when_the_patient_is_not_in_the_graph(monkeypatch):
    monkeypatch.setattr(graph, "reader", lambda: PatientGraphReader(FakeDriver({})))
    assert client.get(f"/agents/graph/{GRAPH_ID}/context", headers=KEY).status_code == 404


def test_names_are_mapped_to_generics_and_herbs_for_the_graph():
    response = client.post("/agents/evaluator/normalise", headers=KEY, json={
        "medicines": ["Brand A 500mg", "Metformin 500mg", "Ubat X"],
        "herbs": ["Jus peria (bitter gourd)", "Kopi"],
    })
    assert response.status_code == 200
    assert response.json() == {
        "medicines": {
            "Brand A 500mg": {"generic": "metformin", "brand": "brand a"},
            "Metformin 500mg": {"generic": "metformin", "brand": None},
            "Ubat X": {"generic": None, "brand": None},
        },
        "herbs": {"Jus peria (bitter gourd)": "bitter gourd", "Kopi": None},
    }
