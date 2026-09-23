from fastapi import APIRouter, Depends, HTTPException

from core import graph
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/graph", tags=["Patient graph"], dependencies=[Depends(verify_internal_service_key)])


@router.get("/{graph_id}/context")
def patient_context(graph_id: str):
    """What the patient graph knows about one patient: conditions, allergies, medicines, herbs, last visit, recent symptoms and readings."""
    reader = graph.reader()
    if reader is None:
        raise HTTPException(status_code=503, detail="No patient graph is configured.")
    try:
        context = reader.context(graph_id)
    except Exception:
        raise HTTPException(status_code=503, detail="The patient graph is unreachable.")
    if context is None:
        raise HTTPException(status_code=404, detail="The patient graph has no such patient.")
    return context
