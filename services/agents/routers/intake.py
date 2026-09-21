from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from typing import List, Dict, Optional
from core.security import verify_internal_service_key
from agents.intake_agent import intake_graph, IntakeState

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
        "next_question": "",
        "is_complete": False
    }

    result = intake_graph.invoke(initial_state)
    return IntakeChatResponse(next_question=result["next_question"], is_complete=result["is_complete"])
