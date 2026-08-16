import base64
import io

from fastapi import APIRouter, File, UploadFile
from PIL import Image

from app.pipeline.normalize import canonicalize
from app.providers.segmentation import get_segmenter
from app.schemas.analysis import ImageResponse

router = APIRouter(prefix="/image", tags=["image"])


def _to_response(png_bytes: bytes, background_removed: bool) -> ImageResponse:
    img = Image.open(io.BytesIO(png_bytes))
    return ImageResponse(
        width=img.width,
        height=img.height,
        background_removed=background_removed,
        image_base64=base64.b64encode(png_bytes).decode("ascii"),
    )


@router.post("/remove-background", response_model=ImageResponse)
async def remove_background(file: UploadFile = File(...)) -> ImageResponse:
    image = await file.read()
    png, removed = get_segmenter().remove_background(image)
    return _to_response(png, removed)


@router.post("/canonicalize", response_model=ImageResponse)
async def canonicalize_image(file: UploadFile = File(...)) -> ImageResponse:
    """Crop-to-content, center, pad, resize — the deterministic 'icon' step (code)."""
    image = await file.read()
    png, _meta = canonicalize(image)
    return _to_response(png, background_removed=False)
