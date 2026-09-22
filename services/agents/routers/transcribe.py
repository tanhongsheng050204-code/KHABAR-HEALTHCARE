from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel
from starlette.concurrency import run_in_threadpool

from agents.transcribe import TranscriptionUnavailable, transcribe
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents", tags=["Transcription"], dependencies=[Depends(verify_internal_service_key)])


class TranscriptResponse(BaseModel):
    text: str


@router.post("/transcribe", response_model=TranscriptResponse)
async def transcribe_audio(request: Request, language: Optional[str] = None, filename: str = "audio.webm"):
    """The raw recording is the request body. Returns text for the doctor to check, never saved here."""
    audio = await request.body()
    try:
        # The upload to Groq can take a while; keep it off the event loop.
        return TranscriptResponse(text=await run_in_threadpool(transcribe, audio, filename, language=language))
    except TranscriptionUnavailable as e:
        raise HTTPException(status_code=503, detail=str(e))
