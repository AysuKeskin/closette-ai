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

    def parse_clothing(self, description):
        raise RuntimeError("upstream 503")

    def extract_ingredients(self, image, filename):
        raise RuntimeError("upstream 503")

    def explain_ingredient(self, name, lang="en"):
        raise RuntimeError("upstream 503")

    def generate_outfit(self, occasion, items, preferences, lang="en"):
        raise RuntimeError("upstream 503")

    def buy_advice(self, candidate, matches, scores, lang="en"):
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


def test_mock_parses_clothing_text_into_attributes():
    parsed = MockProvider().parse_clothing("a beige oversized blazer, very minimal")

    assert parsed.category == "outerwear"
    assert parsed.subcategory == "blazer"
    assert "beige" in parsed.colors
    assert "minimal" in parsed.styles and "oversized" in parsed.styles


def test_mock_extracts_a_stable_ingredient_list():
    image = jpeg_bytes()
    first = MockProvider().extract_ingredients(image, "a.jpg")

    assert len(first) == 4
    assert all(isinstance(x, str) and x for x in first)
    assert first == MockProvider().extract_ingredients(image, "b.jpg")


def test_mock_parse_clothing_is_deterministic_and_safe_when_empty():
    provider = MockProvider()
    assert provider.parse_clothing("navy pleated skirt") == provider.parse_clothing("navy pleated skirt")

    empty = provider.parse_clothing("")
    assert empty.category and empty.colors == [] and empty.styles == []


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
        def explain_ingredient(self, name, lang="en"):
            return marker

    assert _Resilient(_Working(), MockProvider()).explain_ingredient("Retinol") is marker


def test_the_resilient_wrapper_forwards_every_argument(set_env):
    """Guards a signature drift the rest of the suite cannot see.

    Every other test resolves the provider with AI_PROVIDER=mock, and get_provider
    returns the mock unwrapped in that case — so _Resilient's own signatures are
    exercised only in production, where a missing parameter is a 500 on the user's
    first photo.
    """
    from app.providers import _Resilient
    from app.providers.base import AIProvider

    calls = []

    class Recorder(AIProvider):
        def analyze_clothing(self, image, filename, lang="en"):
            calls.append(("analyze_clothing", image, filename, lang))
            return "ok"

        def analyze_beauty(self, image, filename):
            calls.append(("analyze_beauty", image, filename))
            return "ok"

        def extract_ingredients(self, image, filename):
            calls.append(("extract_ingredients", image, filename))
            return []

        def parse_clothing(self, description, lang="en"):
            calls.append(("parse_clothing", description, lang))
            return "ok"

        def explain_ingredient(self, name, lang="en"):
            calls.append(("explain_ingredient", name, lang))
            return "ok"

        def generate_outfit(self, occasion, items, preferences, lang="en", avoid_item_ids=None):
            calls.append(("generate_outfit", occasion, items, preferences, avoid_item_ids, lang))
            return {}

        def buy_advice(self, candidate, matches, scores, lang="en"):
            calls.append(("buy_advice", candidate, matches, scores, lang))
            return {}

    provider = _Resilient(Recorder(), Recorder())

    provider.analyze_clothing(b"x", "x.jpg", "tr")
    provider.parse_clothing("lacivert elbise", "tr")
    provider.explain_ingredient("niacinamide", "tr")
    provider.generate_outfit("plaj", [], [], "tr", ["x"])
    provider.buy_advice({}, [], {}, "tr")

    # Every call reached the primary with its language intact.
    assert all(call[-1] == "tr" for call in calls)
    assert [call[0] for call in calls] == [
        "analyze_clothing", "parse_clothing", "explain_ingredient", "generate_outfit", "buy_advice",
    ]


def test_cataloguing_is_deterministic_but_styling_is_not(set_env, monkeypatch):
    """The stylist ran at temperature 0 like every other call, so the same wardrobe
    and occasion returned one identical outfit forever. Cataloguing must stay at 0."""
    set_env(VLM_API_KEY="test-key")
    from app.providers.vlm_api import OpenAICompatibleVLM

    client = OpenAICompatibleVLM("openai")
    seen = {}

    def fake_chat(messages, json_mode=False, temperature=0.0, **kwargs):
        seen[len(seen)] = temperature
        return '{"itemIds": [], "styles": [], "verdict": "skip"}'

    def fake_vision(system, user, image, filename, operation="unknown"):
        seen[len(seen)] = 0.0  # vision goes through _chat's default
        return {}

    monkeypatch.setattr(client, "_chat", fake_chat)
    monkeypatch.setattr(client, "_vision_json", fake_vision)

    client.analyze_clothing(b"x", "x.jpg", "en")
    catalogue_temp = seen[0]

    seen.clear()
    client.generate_outfit("job interview", [], [], "en")
    stylist_temp = seen[0]

    assert catalogue_temp == 0.0
    assert stylist_temp > 0.5, "styling at temperature 0 gives the same look every time"
