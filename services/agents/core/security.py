import hmac

from fastapi import Security, HTTPException, status
from fastapi.security.api_key import APIKeyHeader
from core.config import settings

API_KEY_HEADER = APIKeyHeader(name="X-Internal-Service-Key", auto_error=False)

async def verify_internal_service_key(api_key: str = Security(API_KEY_HEADER)):
    """
    Enforces that incoming requests to agent endpoints originate from the trusted
    Spring Boot clinical API service.
    """
    # compare_digest takes the same time however much of the key matches, so timing gives nothing away
    if not api_key or not hmac.compare_digest(api_key.encode(), settings.INTERNAL_SERVICE_KEY.encode()):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-Internal-Service-Key header"
        )
    return api_key

