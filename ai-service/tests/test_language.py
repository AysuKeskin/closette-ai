"""Both languages, end to end through the mock provider.

The rule the whole feature rests on: prose is translated, catalogue attributes
are not. A Turkish user still stores "navy" and "minimal", because the backend
matches on those values — the app translates them for display.
"""
import pytest

from app.core.language import DEFAULT, SUPPORTED, normalize, prompt_instruction
from app.providers.mock import MockProvider
from app.providers.vlm_api import OpenAICompatibleVLM

WARDROBE = [
    {"id": "dress-1", "name": "Mini dress", "category": "dress"},
    {"id": "shoes-1", "name": "Ankle boots", "category": "shoes"},
]


@pytest.mark.parametrize("given,expected", [
    ("tr", "tr"), ("TR", "tr"), ("tr-TR", "tr"), ("tr_TR", "tr"),
    ("en", "en"), ("en-GB", "en"),
    ("de", "en"), ("", "en"), (None, "en"), ("  ", "en"),
])
def test_language_tags_are_normalised_to_something_we_ship(given, expected):
    assert normalize(given) == expected


def test_every_supported_language_has_a_prompt_instruction():
    for lang in SUPPORTED:
        assert prompt_instruction(lang).strip()
    # An unknown language must not blow up mid-request.
    assert prompt_instruction("de") == prompt_instruction(DEFAULT)


def test_known_ingredient_is_explained_in_turkish():
    result = MockProvider().explain_ingredient("Niacinamide", "tr")

    assert "B3 vitamini" in result.explanation
    assert result.name == "Niacinamide"  # the ingredient's own name is not translated


def test_unknown_ingredient_admits_it_in_turkish_too():
    result = MockProvider().explain_ingredient("Unobtainium", "tr")

    assert "doğrulanmış bir açıklamamız yok" in result.explanation


def test_outfit_prose_is_turkish_but_item_ids_are_untouched():
    look = MockProvider().generate_outfit("akşam yemeği", WARDROBE, [], "tr")

    assert look["itemIds"] == ["dress-1", "shoes-1"]
    assert look["title"] == "Kombinin"
    assert "akşam yemeği" in look["rationale"]
    assert "parçadan" in look["rationale"]


def test_outfit_falls_back_to_the_turkish_word_for_today():
    assert "bugün" in MockProvider().generate_outfit("", WARDROBE, [], "tr")["rationale"]


@pytest.mark.parametrize("lang", SUPPORTED)
def test_buy_verdicts_are_language_independent(lang):
    # The verdict is a machine value the client branches on; only the prose moves.
    advice = MockProvider().buy_advice({}, [], {"similarItemCount": 3}, lang)

    assert advice["verdict"] == "skip"
    assert advice["explanation"]


def test_buy_explanation_is_turkish():
    advice = MockProvider().buy_advice({}, [], {"similarItemCount": 3}, "tr")

    assert "gardırobunu tekrarlar" in advice["explanation"]


def test_an_unknown_language_quietly_gets_english():
    assert MockProvider().explain_ingredient("Retinol", "de").explanation == \
           MockProvider().explain_ingredient("Retinol", "en").explanation


@pytest.mark.parametrize("turkish,english", [
    ("lacivert minimal elbise", "a navy minimal dress"),
    ("bej oversize ceket", "a beige oversized blazer"),
    ("siyah klasik çanta", "a black classic bag"),
])
def test_a_turkish_description_catalogues_identically_to_its_english_twin(turkish, english):
    # The invariant the whole feature rests on: language changes the words a user
    # types, never the values the wardrobe matches on. (subcategory is excluded —
    # it echoes the noun that matched, and "blazer" is simply more specific than
    # the Turkish "ceket".)
    tr_result = MockProvider().parse_clothing(turkish)
    en_result = MockProvider().parse_clothing(english)

    assert tr_result.category == en_result.category
    assert tr_result.colors == en_result.colors
    assert tr_result.styles == en_result.styles


def test_parsed_attributes_are_always_canonical_english():
    analysis = MockProvider().parse_clothing("lacivert minimal elbise")

    assert analysis.category == "dress"
    assert analysis.colors == ["navy"]
    assert analysis.styles == ["minimal"]
    # Nothing Turkish leaks into a stored value.
    for value in [analysis.subcategory, *analysis.colors, *analysis.styles]:
        assert value.isascii()


def _capture_prompt(monkeypatch, client, method, *args):
    """Runs one VLM call with the network stubbed, and returns the system prompt
    it would have sent."""
    sent = {}

    def fake_chat(messages, json_mode=False):
        sent['system'] = messages[0]['content']
        return '{}'

    monkeypatch.setattr(client, '_chat', fake_chat)
    getattr(client, method)(*args)
    return sent['system']


@pytest.mark.parametrize("method,args", [
    ("explain_ingredient", ("Niacinamide",)),
    ("generate_outfit", ("dinner", [], [])),
    ("buy_advice", ({}, [], {})),
])
def test_every_prose_prompt_carries_the_requested_language(set_env, monkeypatch, method, args):
    # A `lang` parameter that is accepted and then dropped looks like it works:
    # the call succeeds, the answer is just silently in the wrong language.
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")

    turkish = _capture_prompt(monkeypatch, client, method, *args, "tr")
    english = _capture_prompt(monkeypatch, client, method, *args, "en")

    assert prompt_instruction("tr") in turkish
    assert prompt_instruction("en") in english
    assert turkish != english
