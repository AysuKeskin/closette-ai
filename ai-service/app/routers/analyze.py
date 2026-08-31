from fastapi import APIRouter, File, UploadFile

from app.pipeline.color import extract_colors
from app.providers import get_provider
from app.schemas.analysis import (
    BeautyAnalysis,
    ClothingAnalysis,
    ClothingTextRequest,
    ColorInfo,
    IngredientsResponse,
)

router = APIRouter(prefix="/analyze", tags=["analyze"])


@router.post("/clothing", response_model=ClothingAnalysis)
async def analyze_clothing(file: UploadFile = File(...)) -> ClothingAnalysis:
    image = await file.read()
    # VLM does what only a model can: category, subcategory, pattern, style, fit.
    result = get_provider().analyze_clothing(image, file.filename or "upload")
    # Colour is NOT the VLM's job — real pixels → nearest fashion colour (code).
    colors = extract_colors(image)
    if colors:
        result.color_details = [ColorInfo(**c) for c in colors]
        result.colors = [c["name"] for c in colors]
    return result


@router.post("/clothing-text", response_model=ClothingAnalysis)
async def analyze_clothing_text(req: ClothingTextRequest) -> ClothingAnalysis:
    # Natural-language input for Should-I-Buy: no pixels, so colours come from the words here.
    return get_provider().parse_clothing(req.description)


@router.post("/ingredients", response_model=IngredientsResponse)
async def analyze_ingredients(file: UploadFile = File(...)) -> IngredientsResponse:
    # OCR the ingredient list from a photo; the backend cleans the raw tokens.
    image = await file.read()
    return IngredientsResponse(ingredients=get_provider().extract_ingredients(image, file.filename or "upload"))


@router.post("/beauty", response_model=BeautyAnalysis, response_model_by_alias=True)
async def analyze_beauty(file: UploadFile = File(...)) -> BeautyAnalysis:
    image = await file.read()
    return get_provider().analyze_beauty(image, file.filename or "upload")
