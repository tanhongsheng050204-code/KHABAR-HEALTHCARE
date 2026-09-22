"""
Speech to text for the doctor's notes (V2), with Groq's hosted Whisper.
The doctor reads and edits the text before it becomes notes, and the notes are
then treated like typed ones: names and IC numbers removed before any AI drafts
the report. Audio itself can contain names, so this is for the fake-patient
demo; a real clinic needs a transcription service it has an agreement with.
"""
from typing import Optional

import httpx

from agents.evaluator import _data
from core.config import settings

GROQ_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
MODEL = "whisper-large-v3-turbo"
MAX_BYTES = 25 * 1024 * 1024  # Groq's upload limit
SHORTHAND = ["Dx", "c/o", "BD", "TDS", "OD", "ON", "PRN", "PC", "AC", "TCA", "RTC", "T.", "mg"]


class TranscriptionUnavailable(Exception):
    """Transcription is not set up, or the service failed. The doctor can still type."""


def vocabulary_prompt() -> str:
    """Whisper's prompt biases spelling: give it the clinic's shorthand and the drug names we know."""
    generics = [g.title() for g in _data()["generics"]]
    return "Clinic notes. " + ", ".join(SHORTHAND) + ". " + ", ".join(generics) + "."


def transcribe(audio: bytes, filename: str, api_key: Optional[str] = None, client: Optional[httpx.Client] = None,
               language: Optional[str] = None) -> str:
    api_key = api_key if api_key is not None else settings.GROQ_API_KEY
    if not api_key:
        raise TranscriptionUnavailable("Transcription is not set up (no GROQ_API_KEY).")
    if not audio or len(audio) > MAX_BYTES:
        raise TranscriptionUnavailable("The recording is empty or larger than 25 MB.")
    data = {"model": MODEL, "response_format": "json", "prompt": vocabulary_prompt()}
    if language in ("ms", "en", "zh", "ta"):
        data["language"] = language
    own_client = client is None
    client = client or httpx.Client(timeout=60)
    try:
        response = client.post(GROQ_URL, headers={"Authorization": f"Bearer {api_key}"},
                               files={"file": (filename or "audio.webm", audio)}, data=data)
        if response.status_code != 200:
            raise TranscriptionUnavailable(f"The transcription service answered {response.status_code}.")
        return (response.json().get("text") or "").strip()
    except httpx.HTTPError as e:
        raise TranscriptionUnavailable(f"The transcription service is not reachable: {e}") from e
    finally:
        if own_client:
            client.close()
