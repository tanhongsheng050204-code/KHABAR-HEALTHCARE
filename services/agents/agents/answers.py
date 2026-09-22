"""
Doctor-approved answers for follow-up questions. This module only chooses which
approved answer, if any, a patient's reply is asking for; the words the patient
receives are always the doctor's own. Trigger phrases decide first; an optional
model may pick from the same list when no phrase matches, but it can never add
an answer of its own.
"""
import logging
from typing import Any, Optional

from pydantic import BaseModel, Field

from agents.triage import _contains
from core.config import settings
from core.deid import scrub

log = logging.getLogger(__name__)


class AnswerOption(BaseModel):
    id: str
    title: str
    triggers: list[str] = Field(default_factory=list)


class AnswerChoice(BaseModel):
    """What the model must return: the id of one approved answer, or null."""
    answer_id: Optional[str] = Field(default=None, description="The id of the approved answer the patient is asking for, or null")


class AnswerMatch(BaseModel):
    answer_id: Optional[str] = None
    source: Optional[str] = None  # triggers | model


MODEL_PROMPT = """A patient replied to a follow-up message from their Malaysian clinic, in Malay, English,
Chinese, Tamil or a mix. The doctor has approved answers for the questions listed below.
If the reply is clearly asking one of these questions, return that question's id.
If it is not clearly one of them, or it describes a symptom, return null.
Never write an answer yourself.

Approved questions:
{options}"""


def _by_triggers(text: str, options: list[AnswerOption]) -> Optional[str]:
    lowered = text.lower()
    best, best_length = None, 0
    for option in options:
        for trigger in option.triggers:
            trigger = trigger.strip().lower()
            if trigger and len(trigger) > best_length and _contains(lowered, trigger):
                best, best_length = option.id, len(trigger)
    return best


def match_answer(text: str, options: list[AnswerOption], model: Optional[Any] = None) -> AnswerMatch:
    if not options:
        return AnswerMatch()
    found = _by_triggers(text, options)
    if found:
        return AnswerMatch(answer_id=found, source="triggers")
    if model is None:
        return AnswerMatch()
    from langchain_core.messages import HumanMessage, SystemMessage

    listed = "\n".join(f"- id: {o.id} | {o.title} | e.g. {', '.join(o.triggers[:5])}" for o in options)
    try:
        choice = model.invoke([SystemMessage(content=MODEL_PROMPT.format(options=listed)), HumanMessage(content=scrub(text))])
    except Exception as e:  # no answer means a person reads the reply
        log.warning("Answer model failed: %s", e)
        return AnswerMatch()
    if choice.answer_id in {o.id for o in options}:
        return AnswerMatch(answer_id=choice.answer_id, source="model")
    return AnswerMatch()


def default_model():
    """Gemini with structured output when a key is configured, otherwise None (trigger phrases only)."""
    if not settings.GEMINI_API_KEY:
        return None
    from langchain_google_genai import ChatGoogleGenerativeAI

    model = ChatGoogleGenerativeAI(model=settings.GEMINI_MODEL, google_api_key=settings.GEMINI_API_KEY, temperature=0)
    return model.with_structured_output(AnswerChoice)
