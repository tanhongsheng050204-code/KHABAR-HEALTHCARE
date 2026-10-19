from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request
from pydantic import BaseModel

from agents.copilot import AskResult, ScriptResult, ask, call_script, default_ask_model, default_script_model
from agents.meal_reader import MAX_IMAGE_BYTES, MealReaderUnavailable, MealReadResult, read_meal
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents", tags=["Staff copilot and meals"], dependencies=[Depends(verify_internal_service_key)])

_ask_model = default_ask_model()
_script_model = default_script_model()


class AskRequest(BaseModel):
    question: str
    rows: list[dict] = []


@router.post("/copilot/ask", response_model=AskResult)
def ask_khabar(request: AskRequest):
    """Answers from coded rows only and cites them. Empty without a model."""
    return ask(request.question, request.rows, model=_ask_model)


class ScriptRequest(BaseModel):
    language: str = "ms"
    context: dict = {}


@router.post("/copilot/call-script", response_model=ScriptResult)
def draft_call_script(request: ScriptRequest):
    return call_script(request.language, request.context, model=_script_model)


@router.post("/meal/read", response_model=MealReadResult)
async def read_meal_photo(request: Request, mime_type: str = Query(...),
                          consent_confirmed: Optional[str] = Header(default=None, alias="X-Image-Consent-Confirmed")):
    """Food groups visible in a meal photo the patient chose to send. The image is not stored."""
    if consent_confirmed != "true":
        raise HTTPException(status_code=400, detail="Confirm that you agree to send this image.")
    chunks = bytearray()
    async for chunk in request.stream():
        chunks.extend(chunk)
        if len(chunks) > MAX_IMAGE_BYTES:
            raise HTTPException(status_code=413, detail="The image is larger than 8 MB.")
    try:
        return read_meal(bytes(chunks), mime_type)
    except MealReaderUnavailable as e:
        raise HTTPException(status_code=503, detail=str(e))
