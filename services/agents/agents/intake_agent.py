from typing import Any, Dict, List, Optional, TypedDict

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, Field

from core.config import settings
from core.deid import scrub


class IntakeState(TypedDict):
    graph_id: str
    preferred_language: str
    messages: List[Dict[str, str]]
    next_question: str
    is_complete: bool


class IntakeTurn(BaseModel):
    """What the intake model must return on every turn."""
    next_question: str = Field(description="The next question to ask, or a short closing message when complete")
    is_complete: bool = Field(description="True once reason, conditions, medicines and allergies are all covered")


SYSTEM_PROMPT = """You are Khabar's pre-visit intake assistant for a Malaysian clinic.
Collect, in the patient's preferred language, four things before the consultation:
1. The main reason for today's visit and current symptoms.
2. Existing long-term conditions (for example diabetes or high blood pressure).
3. Everything they take: medicines from any clinic, supplements, jamu and traditional Chinese medicine.
4. Allergies to any medicine.
Ask one short, warm question at a time. Never ask for names, IC numbers or phone numbers.
Set is_complete to true only when all four are answered, and then thank the patient."""

# Used when no LLM is configured, so the whole flow runs without an API key.
SCRIPTED_QUESTIONS = {
    "en": [
        "What brings you to the clinic today?",
        "Do you have any long-term conditions, like diabetes or high blood pressure?",
        "What do you take at the moment? Include medicines from other clinics, supplements, jamu or traditional medicine.",
        "Are you allergic to any medicine?",
    ],
    "ms": [
        "Apa sebab Mak Cik / Pak Cik datang ke klinik hari ini?",
        "Ada penyakit jangka panjang, contohnya kencing manis atau darah tinggi?",
        "Apa ubat yang sedang diambil? Termasuk ubat dari klinik lain, supplemen, jamu atau ubat tradisional.",
        "Ada alahan pada mana-mana ubat?",
    ],
}
SCRIPTED_DONE = {
    "en": "Thank you. Your doctor will see this before your consultation.",
    "ms": "Terima kasih. Doktor akan baca maklumat ini sebelum berjumpa.",
}

_BM_ALIASES = {"bm", "ms", "malay", "bahasa melayu", "melayu"}


def _script_language(preferred: str) -> str:
    return "ms" if preferred.strip().lower() in _BM_ALIASES else "en"


def _scripted_turn(state: IntakeState) -> IntakeTurn:
    lang = _script_language(state.get("preferred_language", "English"))
    answered = sum(1 for m in state.get("messages", []) if m.get("role") == "user")
    questions = SCRIPTED_QUESTIONS[lang]
    if answered >= len(questions):
        return IntakeTurn(next_question=SCRIPTED_DONE[lang], is_complete=True)
    return IntakeTurn(next_question=questions[answered], is_complete=False)


def _to_chat_messages(state: IntakeState) -> list:
    chat = [SystemMessage(content=f"{SYSTEM_PROMPT}\nPatient's preferred language: {state.get('preferred_language', 'English')}")]
    for m in state.get("messages", []):
        content = scrub(m.get("content", ""))
        if m.get("role") == "user":
            chat.append(HumanMessage(content=content))
        elif m.get("role") == "assistant":
            chat.append(AIMessage(content=content))
    return chat


def build_intake_graph(llm: Optional[Any] = None):
    """Build the intake workflow. `llm` must return an IntakeTurn from .invoke(messages)."""

    def process_intake(state: IntakeState) -> Dict[str, Any]:
        turn = llm.invoke(_to_chat_messages(state)) if llm is not None else _scripted_turn(state)
        return {"next_question": turn.next_question, "is_complete": turn.is_complete}

    builder = StateGraph(IntakeState)
    builder.add_node("intake", process_intake)
    builder.add_edge(START, "intake")
    builder.add_edge("intake", END)
    return builder.compile()


def default_llm():
    """Gemini with structured output when a key is configured, otherwise None (scripted mode)."""
    if not settings.GEMINI_API_KEY:
        return None
    from langchain_google_genai import ChatGoogleGenerativeAI

    model = ChatGoogleGenerativeAI(model=settings.GEMINI_MODEL, google_api_key=settings.GEMINI_API_KEY, temperature=0.3)
    return model.with_structured_output(IntakeTurn)


intake_graph = build_intake_graph(llm=default_llm())
