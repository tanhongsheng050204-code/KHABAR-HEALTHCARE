from typing import Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from agents.triage import classify_reply
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/followup", tags=["Follow-up Agent"], dependencies=[Depends(verify_internal_service_key)])


class TriageRequest(BaseModel):
    text: str


class TriageResponse(BaseModel):
    level: str
    matched: Optional[str] = None


@router.post("/triage", response_model=TriageResponse)
async def triage_reply(request: TriageRequest):
    result = classify_reply(request.text)
    return TriageResponse(level=result.level, matched=result.matched)
