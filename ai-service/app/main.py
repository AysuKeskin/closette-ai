from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import get_settings
from app.routers import analyze, embed, generate, image, ingredients

app = FastAPI(
    title="Closette AI Service",
    description=(
        "Provider-independent AI layer. The VLM is used only where a model is "
        "truly needed (attributes · OCR · reasoning); colour, normalisation and "
        "similarity plumbing run as classic code."
    ),
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(analyze.router)
app.include_router(embed.router)
app.include_router(generate.router)
app.include_router(image.router)
app.include_router(ingredients.router)


@app.get("/health", tags=["health"])
async def health() -> dict:
    s = get_settings()
    return {
        "status": "ok",
        "vlm": s.ai_provider,
        "segmentation": s.seg_provider,
        "embedding": s.embed_provider,
    }
