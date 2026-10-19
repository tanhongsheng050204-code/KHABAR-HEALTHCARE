"""
English glosses of patient replies, for clinic staff who do not read the patient's language.
A gloss is a machine translation shown next to the patient's own words, never instead of them,
and it plays no part in triage. Without a model there is no gloss.
"""
import logging
from typing import Any, Optional

from pydantic import BaseModel, Field

from core.config import settings
from core.deid import scrub

log = logging.getLogger(__name__)

MAX_TEXT = 2000


class GlossOutput(BaseModel):
    english: str = Field(description="A faithful, plain English translation of the patient's message")


class Gloss(BaseModel):
    english: Optional[str] = None
    source: Optional[str] = None  # model


PROMPT = """Translate a patient's message to their Malaysian clinic into plain English for clinic staff.
The message may be in Malay, Chinese, Tamil, English or a mix, and may use local slang or shorthand.
Translate faithfully: keep every symptom, time, number and medicine name, and add nothing.
Do not advise, summarise, judge urgency, or answer the patient. If it is already English, return it unchanged."""


def gloss(text: str, model: Optional[Any] = None) -> Gloss:
    text = (text or "").strip()
    if not text or model is None:
        return Gloss()
    from langchain_core.messages import HumanMessage, SystemMessage

    try:
        out = model.invoke([SystemMessage(content=PROMPT), HumanMessage(content=scrub(text[:MAX_TEXT]))])
    except Exception as e:  # no gloss: staff read the original
        log.warning("Gloss model failed: %s", e)
        return Gloss()
    english = (getattr(out, "english", "") or "").strip()
    if not english or english == text:
        return Gloss()
    return Gloss(english=english[:MAX_TEXT * 2], source="model")


def default_model():
    if not settings.GEMINI_API_KEY:
        return None
    from langchain_google_genai import ChatGoogleGenerativeAI

    model = ChatGoogleGenerativeAI(model=settings.GEMINI_MODEL, google_api_key=settings.GEMINI_API_KEY, temperature=0)
    return model.with_structured_output(GlossOutput)
