from app.core.config import get_settings
from app.providers.base import AIProvider
from app.providers.mock import MockProvider


def get_provider() -> AIProvider:
    """Resolve the active provider from configuration (NFR-13)."""
    settings = get_settings()
    provider = settings.ai_provider.lower()
    if provider == "qwen":
        from app.providers.qwen import QwenProvider

        return QwenProvider()
    return MockProvider()
