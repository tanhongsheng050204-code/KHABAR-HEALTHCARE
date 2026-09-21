from fastapi.testclient import TestClient

from main import app

client = TestClient(app)
KEY = {"X-Internal-Service-Key": "dev-internal-secret"}


def test_evaluator_endpoint_rejects_calls_without_the_service_key():
    assert client.post("/agents/evaluator/check", json={}).status_code == 401


def test_evaluator_endpoint_returns_findings_for_a_draft():
    draft = {
        "patient": {"allergies": ["penicillin"]},
        "prescription": [{"name": "Amoxicillin", "dose_mg": 500, "times_per_day": 3}],
        "report": {"diagnosis": "Tonsillitis", "plan": "Antibiotics", "follow_up": "PRN"},
    }
    response = client.post("/agents/evaluator/check", json=draft, headers=KEY)
    assert response.status_code == 200
    body = response.json()
    assert body["blocking"] is True
    assert body["findings"][0]["check"] == "allergy"


def test_triage_endpoint_rejects_calls_without_the_service_key():
    assert client.post("/agents/followup/triage", json={"text": "pening"}).status_code == 401


def test_triage_endpoint_classifies_a_reply():
    response = client.post("/agents/followup/triage", json={"text": "Pening dan berpeluh"}, headers=KEY)
    assert response.status_code == 200
    assert response.json() == {"level": "watch", "matched": "pening"}
