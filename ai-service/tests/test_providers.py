"""Provider selection and the mock's behaviour — the seam that lets the app run
with or without a real VLM (NFR-13)."""
import logging

import pytest

from app.providers import _Resilient, get_provider
from app.providers.base import AIProvider
from app.providers.mock import MockProvider
from app.schemas.analysis import BeautyAnalysis, ClothingAnalysis, IngredientExplanation

from tests.images import jpeg_bytes


class _Exploding(AIProvider):
    """Stands in for a real VLM that is down, rate-limited or misconfigured."""

    def analyze_clothing(self, image, filename):
        raise RuntimeError("upstream 503")

    def analyze_beauty(self, image, filename):
        raise RuntimeError("upstream 503")

    def explain_ingredient(self, name):
        raise RuntimeError("upstream 503")

    def generate_outfit(self, occasion, items, preferences):
        raise RuntimeError("upstream 503")

    def buy_advice(self, candidate, matches, scores):
        raise RuntimeError("upstream 503")


def test_mock_analysis_is_stable_for_the_same_image():
    image = jpeg_bytes()

    assert MockProvider().analyze_clothing(image, "a.jpg") == MockProvider().analyze_clothing(image, "b.jpg")


def test_mock_analysis_varies_with_the_image():
    provider = MockProvider()
    results = {provider.analyze_clothing(bytes([i]), "x.jpg").category for i in range(32)}

    assert len(results) > 1


def test_mock_confidence_is_a_probability():
    analysis = MockProvider().analyze_clothing(jpeg_bytes(), "x.jpg")

    assert 0.0 <= analysis.confidence <= 1.0
    assert analysis.seasons and analysis.styles


def test_known_ingredient_is_explained():
    result = MockProvider().explain_ingredient("Niacinamide")

    assert "vitamin b3" in result.explanation.lower()


def test_ingredient_lookup_ignores_case_and_padding():
    padded = MockProvider().explain_ingredient("  HYALURONIC ACID  ")
    plain = MockProvider().explain_ingredient("hyaluronic acid")

    assert padded.explanation == plain.explanation


def test_unknown_ingredient_admits_it_has_no_answer():
    # Better an honest "we don't know" than a confidently invented description.
    result = MockProvider().explain_ingredient("Unobtainium")

    assert result.name == "Unobtainium"
    assert "don't have a verified description" in result.explanation


WARDROBE = [
    {"id": "dress-1", "name": "Mini dress", "category": "dress"},
    {"id": "top-1", "name": "Silk blouse", "category": "top"},
    {"id": "bottom-1", "name": "Midi skirt", "category": "bottom"},
    {"id": "shoes-1", "name": "Ankle boots", "category": "shoes"},
    {"id": "bag-1", "name": "Shoulder bag", "category": "bag"},
]


def test_mock_composer_prefers_a_dress_and_completes_the_look():
    look = MockProvider().generate_outfit("dinner", WARDROBE, [])

    assert look["itemIds"] == ["dress-1", "shoes-1", "bag-1"]
    assert "dinner" in look["rationale"]


def test_mock_composer_falls_back_to_a_top_and_bottom():
    without_dress = [i for i in WARDROBE if i["category"] != "dress"]

    look = MockProvider().generate_outfit("work", without_dress, [])

    assert look["itemIds"] == ["top-1", "bottom-1", "shoes-1", "bag-1"]


def test_mock_composer_accepts_singular_and_plural_categories():
    # The backend sends its lower-cased enum names (dresses/tops/bags), which are
    # plural; the AI service's own vocabulary is singular. The mock speaks both.
    plural_names = {"dress": "dresses", "top": "tops", "bottom": "bottoms",
                    "shoes": "shoes", "bag": "bags"}
    plural = [dict(item, category=plural_names[item["category"]]) for item in WARDROBE]

    assert MockProvider().generate_outfit("dinner", plural, [])["itemIds"] == ["dress-1", "shoes-1", "bag-1"]


def test_mock_composer_handles_an_empty_wardrobe():
    look = MockProvider().generate_outfit("dinner", [], [])

    assert look["itemIds"] == []
    assert look["title"]


@pytest.mark.parametrize("occasion", [None, "", "   "])
def test_mock_composer_names_the_day_when_no_occasion_is_given(occasion):
    assert "your day" in MockProvider().generate_outfit(occasion, WARDROBE, [])["rationale"]


def test_provider_is_the_mock_when_configured(set_env):
    set_env(AI_PROVIDER="mock")

    assert isinstance(get_provider(), MockProvider)


def test_provider_falls_back_to_mock_when_the_real_one_cannot_start(set_env, caplog):
    # No API key → the VLM client refuses to construct; the app must still work.
    set_env(AI_PROVIDER="openai", VLM_API_KEY=None, OPENAI_API_KEY=None,
            QWEN_API_KEY=None, KIMI_API_KEY=None)

    with caplog.at_level(logging.ERROR, logger="app.providers"):
        provider = get_provider()

    assert isinstance(provider, MockProvider)
    assert "init failed" in caplog.text


def test_resilient_provider_falls_back_to_the_mock_and_logs_it(caplog):
    resilient = _Resilient(_Exploding(), MockProvider())

    with caplog.at_level(logging.WARNING, logger="app.providers"):
        clothing = resilient.analyze_clothing(jpeg_bytes(), "x.jpg")
        beauty = resilient.analyze_beauty(jpeg_bytes(), "x.jpg")
        ingredient = resilient.explain_ingredient("Retinol")
        outfit = resilient.generate_outfit("dinner", WARDROBE, [])

    assert isinstance(clothing, ClothingAnalysis)
    assert isinstance(beauty, BeautyAnalysis)
    assert isinstance(ingredient, IngredientExplanation)
    # A dead stylist LLM still returns a wearable look, not an error.
    assert outfit["itemIds"] == ["dress-1", "shoes-1", "bag-1"]
    # Real-vs-fallback has to be visible in the logs, or a dead VLM goes unnoticed.
    assert "analyze_clothing failed" in caplog.text
    assert "upstream 503" in caplog.text


def test_resilient_provider_prefers_the_primary_when_it_works():
    marker = IngredientExplanation(name="Retinol", explanation="from the real model")

    class _Working(_Exploding):
        def explain_ingredient(self, name):
            return marker

    assert _Resilient(_Working(), MockProvider()).explain_ingredient("Retinol") is marker
