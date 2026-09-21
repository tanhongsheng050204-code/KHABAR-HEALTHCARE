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
    GROQ_API_KEY: Optional[str] = None
    
    # Neo4j Graph DB (Read-Only Graph-RAG)
    NEO4J_URI: str = "bolt://localhost:7687"
    NEO4J_USERNAME: str = "neo4j"
    NEO4J_PASSWORD: str = "password"

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

settings = Settings()
