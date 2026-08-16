from fastapi import APIRouter, File, UploadFile

from app.providers.embedding import get_embedder
from app.schemas.analysis import EmbeddingResponse

router = APIRouter(prefix="/embed", tags=["embed"])


@router.post("/item", response_model=EmbeddingResponse)
async def embed_item(file: UploadFile = File(...)) -> EmbeddingResponse:
    """Visual-similarity vector for one item image (computed once, stored in pgvector)."""
    image = await file.read()
    embedder = get_embedder()
    vector = embedder.embed_image(image)
    return EmbeddingResponse(model=embedder.model_name, dim=len(vector), vector=vector)
