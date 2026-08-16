from app.providers.base import AIProvider
from app.schemas.analysis import BeautyAnalysis, ClothingAnalysis, IngredientExplanation

# Placeholder for the real Qwen-VL vision integration. This is where a future
# sprint plugs in the actual model call (DashScope / Qwen API). The rest of the
# system stays untouched because everything depends only on AIProvider (NFR-13).


class QwenProvider(AIProvider):
    def __init__(self) -> None:
        from app.core.config import get_settings

        settings = get_settings()
        if not settings.qwen_api_key:
            raise RuntimeError("QWEN_API_KEY is required when AI_PROVIDER=qwen")
        self.api_key = settings.qwen_api_key

    def analyze_clothing(self, image: bytes, filename: str) -> ClothingAnalysis:
        raise NotImplementedError("Qwen clothing analysis not wired yet")

    def analyze_beauty(self, image: bytes, filename: str) -> BeautyAnalysis:
        raise NotImplementedError("Qwen beauty analysis not wired yet")

    def explain_ingredient(self, name: str) -> IngredientExplanation:
        raise NotImplementedError("Qwen ingredient explanation not wired yet")
