import os
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional

class Settings(BaseSettings):
    PROJECT_NAME: str = "Khabar AI Agent Service"
    VERSION: str = "0.1.0"
    PORT: int = 8000
    
    # Internal service authentication
    INTERNAL_SERVICE_KEY: str = "dev-internal-secret"
    
    # External AI APIs
    GEMINI_API_KEY: Optional[str] = None
    GEMINI_MODEL: str = "gemini-3.6-flash"
    # Spoken check-ins for "voice first" patients: a Gemini speech model and one of its prebuilt voices.
    GEMINI_TTS_MODEL: str = "gemini-2.5-flash-preview-tts"
    GEMINI_TTS_VOICE: str = "Kore"
    # Where the packet reader sends images. Only changed to point at a local stand-in for testing.
    GEMINI_API_BASE: str = "https://generativelanguage.googleapis.com"
    GROQ_API_KEY: Optional[str] = None
    
    # Neo4j patient graph, read-only. Empty means no graph: the agents use what Spring Boot sends.
    NEO4J_URI: str = ""
    NEO4J_USERNAME: str = "neo4j"
    NEO4J_PASSWORD: str = "password"

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

settings = Settings()
