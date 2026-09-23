import base64
import json

import httpx
import pytest
from fastapi.testclient import TestClient

from agents.packet_reader import PacketReaderUnavailable, read_packet
from main import app

KEY = {"X-Internal-Service-Key": "dev-internal-secret"}
client = TestClient(app)


def provider_stub(captured, text=None, status=200):
    text = text or json.dumps({
        "candidates": [{
            "kind": "MEDICINE",
            "brand": "Norvasc",
            "ingredient_text": "Amlodipine",
            "strength": "5 mg",
            "form": "tablet",
            "confidence": "high",
            "evidence": "Norvasc 5 mg amlodipine",
        }],
        "unreadable": False,
        "message": "",
    })

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["auth"] = request.headers.get("x-goog-api-key")
        captured["payload"] = json.loads(request.content)
        return httpx.Response(status, json={"candidates": [{"content": {"parts": [{"text": text}]}}]})

    return httpx.Client(transport=httpx.MockTransport(handler))


def test_packet_read_sends_inline_image_and_maps_ingredient_to_known_generic():
    captured = {}
    result = read_packet(b"fake-jpeg-bytes", "image/jpeg", api_key="test-key", model="test-model",
                         client=provider_stub(captured))

    assert result.candidates[0].brand == "Norvasc"
    assert result.candidates[0].generic_candidate == "amlodipine"
    assert result.candidates[0].review_warning
    assert captured["auth"] == "test-key"
    assert captured["url"].endswith("/models/test-model:generateContent")
    parts = captured["payload"]["contents"][0]["parts"]
    assert base64.b64decode(parts[0]["inline_data"]["data"]) == b"fake-jpeg-bytes"
    assert "Do not infer a diagnosis, dose" in parts[1]["text"]


@pytest.mark.parametrize("mime_type,image", [("image/gif", b"image"), ("image/jpeg", b"")])
def test_invalid_or_empty_image_is_rejected(mime_type, image):
    with pytest.raises(PacketReaderUnavailable):
        read_packet(image, mime_type, api_key="test-key", client=provider_stub({}))


def test_missing_provider_key_is_reported_without_sending_image():
    with pytest.raises(PacketReaderUnavailable, match="not configured"):
        read_packet(b"fake-jpeg", "image/jpeg", api_key=None, client=provider_stub({}))


def test_endpoint_requires_internal_service_key_and_explicit_consent():
    response = client.post("/agents/packet/read?mime_type=image/jpeg", content=b"fake-jpeg")
    assert response.status_code == 401
    response = client.post("/agents/packet/read?mime_type=image/jpeg", content=b"fake-jpeg", headers=KEY)
    assert response.status_code == 400


def test_endpoint_returns_provider_unavailable_without_persisting_upload(monkeypatch):
    monkeypatch.setattr("routers.packet.read_packet", lambda *_: (_ for _ in ()).throw(PacketReaderUnavailable("not configured")))
    response = client.post("/agents/packet/read?mime_type=image/jpeg", content=b"fake-jpeg",
                           headers={**KEY, "X-Image-Consent-Confirmed": "true"})
    assert response.status_code == 503
