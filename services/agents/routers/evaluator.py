from fastapi import APIRouter, Depends
from pydantic import BaseModel

from agents.evaluator import CurrentMed, Draft, Finding, PatientFacts, evaluate, reconcile
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/evaluator", tags=["Evaluator Agent"], dependencies=[Depends(verify_internal_service_key)])


class EvaluationResponse(BaseModel):
    blocking: bool  # True when any CRITICAL finding needs a written reason before finalising
    findings: list[Finding]


@router.post("/check", response_model=EvaluationResponse)
async def check_draft(draft: Draft):
    findings = evaluate(draft)
    return EvaluationResponse(blocking=any(f.severity == "CRITICAL" for f in findings), findings=findings)


class ReconcileRequest(BaseModel):
    patient: PatientFacts = PatientFacts()
    current_meds: list[CurrentMed] = []
    herbs: list[str] = []


class ReconcileResponse(BaseModel):
    findings: list[Finding]


@router.post("/reconcile", response_model=ReconcileResponse)
def reconcile_list(request: ReconcileRequest):
    """Checks the patient's own medicine list before the visit: duplicates, clashes, herbs, allergies."""
    return ReconcileResponse(findings=reconcile(request.current_meds, request.herbs, request.patient))
