from typing import Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from agents.answers import AnswerMatch, AnswerOption, default_model as default_answer_model, match_answer
from agents.triage import default_model, triage
from core import graph
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/followup", tags=["Follow-up Agent"], dependencies=[Depends(verify_internal_service_key)])

_model = default_model()
_answer_model = default_answer_model()


class TriageRequest(BaseModel):
    text: str
    # The patient's random graph ID, so the model can read the reply knowing their conditions and medicines
    graph_id: Optional[str] = None


class TriageResponse(BaseModel):
    level: str
    matched: Optional[str] = None
    source: str = "keywords"
    reason: Optional[str] = None
    missed_dose: bool = False


@router.post("/triage", response_model=TriageResponse)
def triage_reply(request: TriageRequest):
    context = graph.context_or_none(request.graph_id) if _model is not None else None
    result = triage(request.text, model=_model, context=context)
    return TriageResponse(level=result.level, matched=result.matched, source=result.source, reason=result.reason,
                          missed_dose=result.missed_dose)


class AnswerRequest(BaseModel):
    text: str
    options: list[AnswerOption] = []


@router.post("/answer", response_model=AnswerMatch)
def choose_answer(request: AnswerRequest):
    """Which doctor-approved answer, if any, the reply asks for. Returns an id, never answer text."""
    return match_answer(request.text, request.options, model=_answer_model)
