from abc import ABC, abstractmethod

from app.schemas.analysis import BeautyAnalysis, ClothingAnalysis, IngredientExplanation


class AIProvider(ABC):
    """Contract every AI backend implements. Swapping Qwen/OpenAI/Gemini is a
    matter of adding one subclass — nothing else in the system changes (NFR-13)."""

    @abstractmethod
    def analyze_clothing(self, image: bytes, filename: str, lang: str = "en") -> ClothingAnalysis:
        """Catalogue a garment from its photo. ``lang`` reaches only the free-text
        subcategory, which the user reads; every fixed vocabulary stays English."""
        ...

    @abstractmethod
    def analyze_beauty(self, image: bytes, filename: str) -> BeautyAnalysis:
        ...

    @abstractmethod
    def extract_ingredients(self, image: bytes, filename: str) -> list[str]:
        """OCR a photo of a product's ingredient list into a list of ingredient names.
        A genuine model use (reading text from pixels); the result is cleaned by classic code."""
        ...

    @abstractmethod
    def parse_clothing(self, description: str, lang: str = "en") -> ClothingAnalysis:
        """Parse a free-text garment description ("a beige oversized blazer") into
        structured attributes. Used by Should-I-Buy's natural-language input, so the
        user never fills a category/colour/style form. No pixels here, so colours
        come from the words (unlike analyze_clothing, where colour is from the image)."""
        ...

    @abstractmethod
    def explain_ingredient(self, name: str, lang: str = "en") -> IngredientExplanation:
        ...

    @abstractmethod
    def generate_outfit(self, occasion: str, items: list[dict], preferences: list[str],
                        lang: str = "en", avoid_item_ids: list[str] | None = None) -> dict:
        """Compose one complete outfit from the given owned items (RAG: the items
        are the retrieved context). Returns {itemIds, title, rationale}."""
        ...

    @abstractmethod
    def buy_advice(self, candidate: dict, matches: list[dict], scores: dict,
                   lang: str = "en") -> dict:
        """Should-I-buy verdict (RAG: retrieved similar owned items + computed
        scores as context). Returns {verdict: buy|maybe|skip, explanation}."""
        ...
