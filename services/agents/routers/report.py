from fastapi import APIRouter, Depends
from pydantic import BaseModel

from agents.report_agent import ReportDraft, draft_from_notes
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/report", tags=["Report Agent"], dependencies=[Depends(verify_internal_service_key)])


class DraftRequest(BaseModel):
    notes: str


@router.post("/draft", response_model=ReportDraft)
async def draft_report(request: DraftRequest):
    """Structures the doctor's notes. Sections the doctor did not write stay empty."""
    return draft_from_notes(request.notes)
