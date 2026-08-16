from fastapi import APIRouter, File, UploadFile

from app.pipeline.color import extract_colors
from app.providers import get_provider
from app.schemas.analysis import BeautyAnalysis, ClothingAnalysis, ColorInfo

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


@router.post("/beauty", response_model=BeautyAnalysis, response_model_by_alias=True)
async def analyze_beauty(file: UploadFile = File(...)) -> BeautyAnalysis:
    image = await file.read()
    return get_provider().analyze_beauty(image, file.filename or "upload")
