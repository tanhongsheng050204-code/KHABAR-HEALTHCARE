import pytest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert "service" in data

def test_intake_chat_unauthorized():
    # Calling intake without X-Internal-Service-Key should return 401
    payload = {
        "graph_id": "test-uuid-1234",
        "preferred_language": "English",
        "messages": []
    }
    response = client.post("/agents/intake/chat", json=payload)
    assert response.status_code == 401

def test_intake_chat_authorized():
    payload = {
        "graph_id": "test-uuid-1234",
        "preferred_language": "Malay",
        "messages": [
            {"role": "user", "content": "Saya batuk dan demam sejak semalam."}
        ]
    }
    headers = {
        "X-Internal-Service-Key": "dev-internal-secret"
    }
    response = client.post("/agents/intake/chat", json=payload, headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert "next_question" in data
    assert "is_complete" in data

