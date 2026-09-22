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
    assert response.json() == {"level": "watch", "matched": "pening", "source": "keywords", "reason": None}


def test_report_draft_endpoint_rejects_calls_without_the_service_key():
    assert client.post("/agents/report/draft", json={"notes": "x"}).status_code == 401


def test_report_draft_endpoint_structures_the_notes():
    notes = "Dx: HTN\nT. Amlodipine 5mg 1/1 OD\nTCA 1/52"
    response = client.post("/agents/report/draft", json={"notes": notes}, headers=KEY)
    assert response.status_code == 200
    body = response.json()
    assert body["diagnosis"] == "HTN"
    assert body["follow_up_weeks"] == 1
    assert body["prescription"][0] == {
        "raw": "T. Amlodipine 5mg 1/1 OD", "name": "Amlodipine", "strength_mg": 5.0, "units_per_dose": 1.0,
        "times_per_day": 1, "times_of_day": ["morning"], "timing": None, "as_needed": False, "dose_mg": 5.0,
    }


def test_summary_endpoint_builds_the_patients_summary_and_whatsapp_text():
    rx = client.post("/agents/report/draft", json={"notes": "T. Metformin 500mg 1/1 BD PC"}, headers=KEY).json()["prescription"]
    response = client.post("/agents/summary/build", json={"prescription": rx, "language": "ms", "follow_up_weeks": 2}, headers=KEY)
    assert response.status_code == 200
    body = response.json()
    assert body["medicines"][0]["how"] == "1 biji, pagi dan malam, selepas makan."
    assert "Datang semula dalam 2 minggu." in body["text"]


def test_summary_endpoint_rejects_calls_without_the_service_key():
    assert client.post("/agents/summary/build", json={"prescription": [], "language": "ms"}).status_code == 401


def test_previsit_report_endpoint_rejects_calls_without_the_service_key():
    assert client.post("/agents/intake/report", json={"messages": []}).status_code == 401


def test_previsit_report_endpoint_lays_out_the_intake_for_the_doctor():
    messages = [
        {"role": "assistant", "content": "What do you take at the moment?"},
        {"role": "user", "content": "Brand A 500mg and bitter gourd juice"},
    ]
    response = client.post("/agents/intake/report", json={"messages": messages}, headers=KEY)
    assert response.status_code == 200
    body = response.json()
    assert body["medicines"] == [{"as_written": "Brand A 500mg", "generic": "metformin"}]
    assert body["herbs"] == ["bitter gourd"]
