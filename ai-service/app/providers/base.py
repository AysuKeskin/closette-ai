from abc import ABC, abstractmethod

from app.schemas.analysis import BeautyAnalysis, ClothingAnalysis, IngredientExplanation


class AIProvider(ABC):
    """Contract every AI backend implements. Swapping Qwen/OpenAI/Gemini is a
    matter of adding one subclass — nothing else in the system changes (NFR-13)."""

    @abstractmethod
    def analyze_clothing(self, image: bytes, filename: str) -> ClothingAnalysis:
        ...

    @abstractmethod
    def analyze_beauty(self, image: bytes, filename: str) -> BeautyAnalysis:
        ...

    @abstractmethod
    def explain_ingredient(self, name: str) -> IngredientExplanation:
        ...

    @abstractmethod
    def generate_outfit(self, occasion: str, items: list[dict], preferences: list[str]) -> dict:
        """Compose one complete outfit from the given owned items (RAG: the items
        are the retrieved context). Returns {itemIds, title, rationale}."""
        ...

    @abstractmethod
    def buy_advice(self, candidate: dict, matches: list[dict], scores: dict) -> dict:
        """Should-I-buy verdict (RAG: retrieved similar owned items + computed
        scores as context). Returns {verdict: buy|maybe|skip, explanation}."""
        ...
