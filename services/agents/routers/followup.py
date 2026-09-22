from typing import Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from agents.triage import default_model, triage
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/followup", tags=["Follow-up Agent"], dependencies=[Depends(verify_internal_service_key)])

_model = default_model()


class TriageRequest(BaseModel):
    text: str


class TriageResponse(BaseModel):
    level: str
    matched: Optional[str] = None
    source: str = "keywords"
    reason: Optional[str] = None


@router.post("/triage", response_model=TriageResponse)
def triage_reply(request: TriageRequest):
    result = triage(request.text, model=_model)
    return TriageResponse(level=result.level, matched=result.matched, source=result.source, reason=result.reason)
