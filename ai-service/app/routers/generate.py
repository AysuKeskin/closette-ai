from fastapi import APIRouter

from app.providers import get_provider
from app.schemas.analysis import (
    BuyAdviceRequest,
    BuyAdviceResponse,
    OutfitRequest,
    OutfitSuggestion,
)

router = APIRouter(prefix="/generate", tags=["generate"])


@router.post("/outfit", response_model=OutfitSuggestion)
async def generate_outfit(req: OutfitRequest) -> OutfitSuggestion:
    """Agentic RAG: compose one outfit from the retrieved owned items."""
    items = [i.model_dump() for i in req.items]
    data = get_provider().generate_outfit(
        req.occasion, items, req.preferences, req.lang, req.avoid_item_ids)
    return OutfitSuggestion(**data)


@router.post("/buy-advice", response_model=BuyAdviceResponse)
async def buy_advice(req: BuyAdviceRequest) -> BuyAdviceResponse:
    """RAG: verdict grounded in retrieved similar owned items + computed scores."""
    data = get_provider().buy_advice(req.candidate, req.matches, req.scores, req.lang)
    return BuyAdviceResponse(**data)
