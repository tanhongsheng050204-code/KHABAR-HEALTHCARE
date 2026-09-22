import httpx
import pytest
from fastapi.testclient import TestClient

from agents import transcribe as transcribe_module
from agents.transcribe import TranscriptionUnavailable, transcribe, vocabulary_prompt
from main import app

KEY = {"X-Internal-Service-Key": "dev-internal-secret"}


def groq_stub(captured, reply=None, status=200):
    """A stand-in for Groq's transcription endpoint that records the request."""
    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["auth"] = request.headers.get("authorization")
        captured["body"] = request.content
        return httpx.Response(status, json=reply if reply is not None else {"text": "Dx T2DM. T Metformin 500 mg BD."})
    return httpx.Client(transport=httpx.MockTransport(handler))


def test_audio_is_sent_to_whisper_with_the_drug_names_as_a_hint():
    captured = {}
    text = transcribe(b"fake-audio", "visit.webm", api_key="gsk-test", client=groq_stub(captured), language="ms")

    assert text == "Dx T2DM. T Metformin 500 mg BD."
    assert captured["url"].endswith("/openai/v1/audio/transcriptions")
    assert captured["auth"] == "Bearer gsk-test"
    body = captured["body"].decode("utf-8", errors="ignore")
    assert "whisper-large-v3-turbo" in body
    assert "metformin" in body.lower() and "amlodipine" in body.lower()
    assert 'name="language"' in body and "ms" in body


def test_without_a_key_transcription_is_unavailable():
    with pytest.raises(TranscriptionUnavailable):
        transcribe(b"x", "a.webm", api_key=None)


def test_a_groq_error_is_reported_as_unavailable():
    with pytest.raises(TranscriptionUnavailable):
        transcribe(b"x", "a.webm", api_key="gsk-test", client=groq_stub({}, reply={"error": "bad"}, status=500))


def test_the_hint_names_common_shorthand_and_generics():
    prompt = vocabulary_prompt()
    assert "TCA" in prompt and "BD" in prompt and "Metformin" in prompt


client = TestClient(app)


def test_the_endpoint_needs_the_service_key():
    assert client.post("/agents/transcribe", content=b"x").status_code == 401


def test_the_endpoint_says_so_when_transcription_is_not_set_up(monkeypatch):
    monkeypatch.setattr(transcribe_module.settings, "GROQ_API_KEY", None)
    response = client.post("/agents/transcribe", content=b"audio", headers={**KEY, "Content-Type": "audio/webm"})
    assert response.status_code == 503


def test_the_endpoint_returns_the_text(monkeypatch):
    monkeypatch.setattr("routers.transcribe.transcribe", lambda audio, filename, language=None: "c/o pening 3/7")
    response = client.post("/agents/transcribe?language=ms", content=b"audio", headers={**KEY, "Content-Type": "audio/webm"})
    assert response.status_code == 200
    assert response.json() == {"text": "c/o pening 3/7"}
