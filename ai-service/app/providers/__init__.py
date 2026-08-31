import logging

from app.core.config import get_settings
from app.providers.base import AIProvider
from app.providers.mock import MockProvider

log = logging.getLogger(__name__)


class _Resilient(AIProvider):
    """Try the real VLM; on any failure fall back to the deterministic mock so the
    app never hard-breaks. Failures are logged so real-vs-fallback is visible."""

    def __init__(self, primary: AIProvider, fallback: AIProvider) -> None:
        self.primary = primary
        self.fallback = fallback

    def _call(self, method: str, *args):
        try:
            return getattr(self.primary, method)(*args)
        except Exception as e:  # noqa: BLE001
            log.warning("VLM %s failed (%s); falling back to mock", method, e)
            return getattr(self.fallback, method)(*args)

    def analyze_clothing(self, image, filename):
        return self._call("analyze_clothing", image, filename)

    def analyze_beauty(self, image, filename):
        return self._call("analyze_beauty", image, filename)

    def parse_clothing(self, description):
        return self._call("parse_clothing", description)

    def extract_ingredients(self, image, filename):
        return self._call("extract_ingredients", image, filename)

    def explain_ingredient(self, name, lang="en"):
        return self._call("explain_ingredient", name, lang)

    def generate_outfit(self, occasion, items, preferences, lang="en"):
        return self._call("generate_outfit", occasion, items, preferences, lang)

    def buy_advice(self, candidate, matches, scores, lang="en"):
        return self._call("buy_advice", candidate, matches, scores, lang)


def get_provider() -> AIProvider:
    """Resolve the active VLM from configuration (NFR-13)."""
    settings = get_settings()
    name = settings.ai_provider.lower()
    if name == "mock":
        return MockProvider()

    try:
        from app.providers.vlm_api import OpenAICompatibleVLM

        real = OpenAICompatibleVLM(name)
    except Exception as e:  # noqa: BLE001 — missing key / bad config
        log.error("VLM provider '%s' init failed (%s); using mock", name, e)
        return MockProvider()

    return _Resilient(real, MockProvider()) if settings.vlm_fallback_to_mock else real
