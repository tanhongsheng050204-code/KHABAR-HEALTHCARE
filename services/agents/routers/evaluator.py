from fastapi import APIRouter, Depends
from pydantic import BaseModel

from agents.evaluator import Draft, Finding, evaluate
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/evaluator", tags=["Evaluator Agent"], dependencies=[Depends(verify_internal_service_key)])


class EvaluationResponse(BaseModel):
    blocking: bool  # True when any CRITICAL finding needs a written reason before finalising
    findings: list[Finding]


@router.post("/check", response_model=EvaluationResponse)
async def check_draft(draft: Draft):
    findings = evaluate(draft)
    return EvaluationResponse(blocking=any(f.severity == "CRITICAL" for f in findings), findings=findings)
