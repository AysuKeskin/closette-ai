from fastapi import APIRouter

from app.providers import get_provider
from app.schemas.analysis import IngredientExplanation, IngredientRequest

router = APIRouter(prefix="/ingredients", tags=["ingredients"])


@router.post("/explain", response_model=IngredientExplanation)
async def explain_ingredient(request: IngredientRequest) -> IngredientExplanation:
    return get_provider().explain_ingredient(request.name, request.lang)
