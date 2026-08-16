from pydantic import BaseModel, Field


class ColorInfo(BaseModel):
    """A dominant garment colour — from classic code (LAB nearest), not the VLM."""

    name: str
    hex: str
    percentage: int


class ClothingAnalysis(BaseModel):
    """Structured clothing analysis. Matches the backend's ClothingAnalysis record.

    ``colors`` (names) stays for backend compatibility; ``color_details`` adds the
    richer {name, hex, percentage} computed by the colour pipeline.
    """

    category: str
    subcategory: str
    colors: list[str] = Field(default_factory=list)
    color_details: list[ColorInfo] = Field(default_factory=list)
    pattern: str
    styles: list[str] = Field(default_factory=list)
    seasons: list[str] = Field(default_factory=list)
    # 0..1 confidence drives the "is that right?" confirmation UX (NFR-08).
    confidence: float = 0.6


class BeautyAnalysis(BaseModel):
    brand: str
    product_name: str = Field(alias="productName")
    category: str
    confidence: float = 0.6

    model_config = {"populate_by_name": True}


class IngredientRequest(BaseModel):
    name: str


class IngredientExplanation(BaseModel):
    name: str
    explanation: str


class EmbeddingResponse(BaseModel):
    """Visual-similarity vector for pgvector search. Computed once per item."""

    model: str
    dim: int
    vector: list[float]


class ImageResponse(BaseModel):
    """A processed image returned as base64 PNG (background removal / canonicalise)."""

    format: str = "png"
    width: int
    height: int
    background_removed: bool = False
    image_base64: str
