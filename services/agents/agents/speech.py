"""
Spoken check-ins for patients who chose "voice first": the clinic's fixed check-in wording read aloud and encoded
as MP3 for a Telegram voice message. Only clinic wording is ever spoken, never a patient's words, so nothing about
a patient is sent. Results are kept in memory by text, so each message is made once.
"""
import base64
from collections import OrderedDict
from typing import Optional

import httpx

from core.config import settings

GEMINI_PATH = "/v1beta/models/{model}:generateContent"
SAMPLE_RATE = 24000
MAX_TEXT = 1000
_cache: "OrderedDict[str, bytes]" = OrderedDict()
CACHE_SIZE = 64


class SpeechUnavailable(Exception):
    """No speech provider, or it failed."""


def pcm_to_mp3(pcm: bytes, sample_rate: int = SAMPLE_RATE) -> bytes:
    import lameenc

    encoder = lameenc.Encoder()
    encoder.set_bit_rate(48)
    encoder.set_in_sample_rate(sample_rate)
    encoder.set_channels(1)
    encoder.set_quality(5)
    return bytes(encoder.encode(pcm) + encoder.flush())


def say(text: str, api_key: Optional[str] = None, model: Optional[str] = None, client: Optional[httpx.Client] = None) -> bytes:
    text = (text or "").strip()[:MAX_TEXT]
    if not text:
        raise SpeechUnavailable("Nothing to say.")
    if text in _cache:
        _cache.move_to_end(text)
        return _cache[text]
    api_key = api_key if api_key is not None else settings.GEMINI_API_KEY
    if not api_key:
        raise SpeechUnavailable("Speech is not configured.")
    payload = {
        "contents": [{"parts": [{"text": "Read this message aloud warmly and clearly, in the language it is written in:\n" + text}]}],
        "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": settings.GEMINI_TTS_VOICE}}},
        },
    }
    own = client is None
    client = client or httpx.Client(timeout=60)
    try:
        response = client.post(settings.GEMINI_API_BASE.rstrip("/") + GEMINI_PATH.format(model=model or settings.GEMINI_TTS_MODEL),
                               headers={"x-goog-api-key": api_key}, json=payload)
        if response.status_code != 200:
            raise SpeechUnavailable(f"The speech provider answered {response.status_code}.")
        part = response.json()["candidates"][0]["content"]["parts"][0]
        data = part.get("inlineData") or part.get("inline_data")
        pcm = base64.b64decode(data["data"])
    except (KeyError, IndexError, TypeError, ValueError, httpx.HTTPError) as e:
        raise SpeechUnavailable("No audio came back.") from e
    finally:
        if own:
            client.close()
    mp3 = pcm_to_mp3(pcm)
    _cache[text] = mp3
    if len(_cache) > CACHE_SIZE:
        _cache.popitem(last=False)
    return mp3
