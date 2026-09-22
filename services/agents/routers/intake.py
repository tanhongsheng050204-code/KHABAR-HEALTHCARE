from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from typing import Any, List, Dict, Optional
from core.security import verify_internal_service_key
from agents.intake_agent import intake_graph, IntakeState
from agents.previsit import PreVisitReport, build_previsit_report

router = APIRouter(
    prefix="/agents/intake",
    tags=["Intake Agent"],
    dependencies=[Depends(verify_internal_service_key)]
)

class ChatMessage(BaseModel):
    role: str # "user" or "assistant"
    content: str

class IntakeChatRequest(BaseModel):
    graph_id: str = Field(..., description="De-identified patient graph UUID")
    preferred_language: str = Field("English", description="BM, English, Chinese, or Tamil")
    messages: List[ChatMessage] = Field(default_factory=list)
    context: Dict[str, Any] = Field(default_factory=dict, description="What the clinic already knows: medicines, allergies, last_diagnosis. No identifiers.")

class IntakeChatResponse(BaseModel):
    next_question: str
    is_complete: bool

@router.post("/chat", response_model=IntakeChatResponse)
async def process_intake_chat(request: IntakeChatRequest):
    """
    Executes a step in the intake conversation using LangGraph.
    Requires internal service key from Spring Boot.
    """
    initial_state: IntakeState = {
        "graph_id": request.graph_id,
        "preferred_language": request.preferred_language,
        "messages": [m.model_dump() for m in request.messages],
        "context": request.context,
        "next_question": "",
        "is_complete": False
    }

    result = intake_graph.invoke(initial_state)
    return IntakeChatResponse(next_question=result["next_question"], is_complete=result["is_complete"])


class PreVisitRequest(BaseModel):
    messages: List[ChatMessage] = Field(default_factory=list)


@router.post("/report", response_model=PreVisitReport)
def previsit_report(request: PreVisitRequest):
    """Lay out a finished intake chat for the doctor: answers by topic, medicines, herbs, allergies and warning signs."""
    return build_previsit_report([m.model_dump() for m in request.messages])
