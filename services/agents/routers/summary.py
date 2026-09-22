from typing import Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from agents.prescription import ParsedRx
from agents.summary import PatientSummary, build_summary
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/summary", tags=["Patient Summary"], dependencies=[Depends(verify_internal_service_key)])


class SummaryRequest(BaseModel):
    prescription: list[ParsedRx]
    language: str
    follow_up_weeks: Optional[float] = None
    fasting: bool = False


class SummaryResponse(PatientSummary):
    text: str


@router.post("/build", response_model=SummaryResponse)
async def build(request: SummaryRequest):
    summary = build_summary(request.prescription, request.language, request.follow_up_weeks, request.fasting)
    return SummaryResponse(**summary.model_dump(), text=summary.as_text())
