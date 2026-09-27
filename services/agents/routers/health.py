from fastapi import APIRouter
from core.config import settings

router = APIRouter()

@router.get("/health", tags=["Health"])
@router.get("/agents/health", tags=["Health"])
async def health_check():
    """
    Public health check endpoint for container orchestrators and monitoring.
    """
    return {
        "status": "healthy",
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION
    }
