from fastapi import APIRouter, Depends
from pydantic import BaseModel

from agents.evaluator import CurrentMed, Draft, Finding, PatientFacts, evaluate, herb_of, reconcile, with_graph, written_as
from core import graph
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/evaluator", tags=["Evaluator Agent"], dependencies=[Depends(verify_internal_service_key)])


class EvaluationResponse(BaseModel):
    blocking: bool  # True when any CRITICAL finding needs a written reason before finalising
    findings: list[Finding]


@router.post("/check", response_model=EvaluationResponse)
def check_draft(draft: Draft):
    findings = evaluate(with_graph(draft, graph.context_or_none(draft.graph_id)))
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


class NormaliseRequest(BaseModel):
    medicines: list[str] = []
    herbs: list[str] = []


@router.post("/normalise")
def normalise(request: NormaliseRequest):
    """
    For Spring Boot's patient-graph writer: each medicine's generic (and the brand it was written as),
    and each remedy's herb, from the same drug data the safety checks use. Unknown names map to null.
    """
    return {
        "medicines": {name: written_as(name) for name in request.medicines},
        "herbs": {name: herb_of(name) for name in request.herbs},
    }
