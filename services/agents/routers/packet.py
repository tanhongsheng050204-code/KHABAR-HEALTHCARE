from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request

from agents.packet_reader import MAX_IMAGE_BYTES, PacketReaderUnavailable, PacketReadResult, read_packet
from core.security import verify_internal_service_key

router = APIRouter(prefix="/agents/packet", tags=["Packet reader"], dependencies=[Depends(verify_internal_service_key)])


@router.post("/read", response_model=PacketReadResult)
async def read_medicine_packet(request: Request, mime_type: str = Query(...),
                               consent_confirmed: str | None = Header(default=None, alias="X-Image-Consent-Confirmed")):
    """Read a user-confirmed, ephemeral image. The image is not stored by this service."""
    if consent_confirmed != "true":
        raise HTTPException(status_code=400, detail="Confirm that you agree to send this image for label reading.")
    declared_size = request.headers.get("content-length")
    if declared_size and declared_size.isdigit() and int(declared_size) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="The packet image is larger than 8 MB.")
    try:
        chunks = bytearray()
        async for chunk in request.stream():
            chunks.extend(chunk)
            if len(chunks) > MAX_IMAGE_BYTES:
                raise HTTPException(status_code=413, detail="The packet image is larger than 8 MB.")
        return read_packet(bytes(chunks), mime_type)
    except PacketReaderUnavailable as e:
        raise HTTPException(status_code=503, detail=str(e))
