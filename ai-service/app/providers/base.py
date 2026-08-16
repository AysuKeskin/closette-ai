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
