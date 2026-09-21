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

    def fake_chat(messages, json_mode=False, **kwargs):
        sent['system'] = messages[0]['content']
        sent.update(kwargs)
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


def test_no_english_title_leaks_when_the_model_gives_none(set_env, monkeypatch):
    # A hard-coded English default here reached Turkish users as "Your look".
    # Empty means the backend fills its own localized title instead.
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")
    monkeypatch.setattr(client, "_chat", lambda *a, **k: '{"itemIds":["a"],"rationale":"Uygun."}')

    look = client.generate_outfit("akşam yemeği", [], [], "tr")

    assert look["title"] == ""
    assert look["itemIds"] == ["a"]


def test_the_stylist_is_told_not_to_refuse_a_real_occasion(set_env, monkeypatch):
    # The prompt used to refuse any occasion it judged unclear, and turned down
    # "plaj günü" — a perfectly ordinary one — instead of composing a look.
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")
    sent = {}
    monkeypatch.setattr(client, "_chat",
                        lambda messages, **k: sent.setdefault("system", messages[0]["content"]) and "{}" or "{}")

    client.generate_outfit("plaj günü", [], [], "tr")

    system = sent["system"]
    assert "A real occasion ALWAYS gets an outfit" in system
    assert "Never refuse a genuine occasion" in system


def test_the_free_text_subcategory_follows_the_language(set_env):
    # Everything else a garment is catalogued with is a closed vocabulary the app
    # translates. Subcategory is free text the user reads back as their item's
    # name, so it has to arrive already in their language.
    set_env(AI_PROVIDER="mock")
    from app.providers.mock import _SUBCATEGORY_TR, MockProvider

    provider = MockProvider()
    image = b"a-consistent-image"

    english = provider.analyze_clothing(image, "x.jpg", "en")
    turkish = provider.analyze_clothing(image, "x.jpg", "tr")

    # Which garment the hash picks is incidental; that the two are the same
    # garment named in two languages is the point.
    assert turkish.subcategory == _SUBCATEGORY_TR[english.subcategory]
    assert turkish.subcategory != english.subcategory
    # The catalogue values are the same item in both languages.
    assert english.category == turkish.category
    assert english.colors == turkish.colors
    assert english.styles == turkish.styles
    assert english.seasons == turkish.seasons


def test_a_turkish_description_keeps_the_turkish_noun(set_env):
    set_env(AI_PROVIDER="mock")
    from app.providers.mock import MockProvider

    parsed = MockProvider().parse_clothing("lacivert bir elbise", "tr")

    assert parsed.subcategory == "elbise"
    assert parsed.category == "dress"
    assert parsed.colors == ["navy"]


def test_the_clothing_prompt_carries_the_subcategory_language(set_env, monkeypatch):
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")
    seen = {}

    def fake_vision(system, user, image, filename):
        seen["system"] = system
        return {}

    monkeypatch.setattr(client, "_vision_json", fake_vision)
    client.analyze_clothing(b"x", "x.jpg", "tr")

    assert "Write subcategory in Turkish" in seen["system"]
    assert "stays canonical English" in seen["system"]


def test_the_stylist_is_told_to_match_formality_and_finish_the_look(set_env, monkeypatch):
    """The three rules that made the difference in measurement.

    Without them the same summer look came back for a beach day and a wedding,
    half the looks had no shoes, and every repeat of one occasion was identical.
    """
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")
    seen = {}
    monkeypatch.setattr(client, "_chat",
                        lambda messages, **kw: seen.setdefault("system", messages[0]["content"]) and "{}" or "{}")

    client.generate_outfit("yaz düğünü", [], [], "tr")
    system = seen["system"]

    # Reason before choosing, rather than emitting ids straight away.
    assert '"plan"' in system
    # Formality applied against the item's own style words, not left to taste.
    assert "MECHANICALLY" in system
    assert "FORBIDDEN" in system
    # A look without shoes is incomplete.
    assert "MUST include exactly one pair of" in system
    # Don't return the same default every time.
    assert "pick a different one" in system


def test_cataloguing_uses_a_closed_style_vocabulary(set_env, monkeypatch):
    """Styles are translated for display and reasoned over by the stylist, so an
    invented word is a word nothing downstream understands."""
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")
    seen = {}
    monkeypatch.setattr(client, "_vision_json",
                        lambda system, *a, **k: seen.setdefault("system", system) and {} or {})

    client.analyze_clothing(b"x", "x.jpg", "tr")
    system = seen["system"]

    for word in ("minimal", "cottagecore", "streetwear", "formal"):
        assert word in system
    assert "Never invent a word outside this list" in system


def test_a_gown_must_be_flagged_formal(set_env, monkeypatch):
    """`formal` is what keeps an evening gown out of a coffee suggestion. The model
    treated it as one adjective among many and reached for `elegant` instead, so the
    flag has to be stated as required rather than offered as a choice."""
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")
    seen = {}
    monkeypatch.setattr(client, "_vision_json",
                        lambda system, *a, **k: seen.setdefault("system", system) and {} or {})

    client.analyze_clothing(b"x", "x.jpg", "tr")
    system = seen["system"]

    assert "REQUIRED FLAG" in system
    assert "abiye" in system          # the Turkish term the app's users actually type
    assert "must NOT carry `formal`" in system


def test_the_stylist_reasons_about_what_the_occasion_physically_involves(set_env, monkeypatch):
    """Formality and season are not enough on their own.

    An edgy leather boot is a perfectly good casual shoe and still the wrong thing
    for a beach day, so the plan has to ask what the occasion physically involves
    before anything is chosen.
    """
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")
    seen = {}
    monkeypatch.setattr(client, "_chat",
                        lambda messages, **kw: seen.setdefault("system", messages[0]["content"]) and "{}" or "{}")

    client.generate_outfit("plaj günü", [], [], "tr")
    system = seen["system"]

    assert "PRACTICAL" in system
    assert "no boots or closed shoes for a beach" in system
    # Season applied against the item's own tags, the same way formality is.
    assert "never put a summer-only piece into a winter occasion" in system
    # Swimwear and gym clothes do not travel.
    assert "swimwear belongs at a beach or pool and nowhere else" in system


def test_formality_levels_are_spelled_out_with_examples(set_env, monkeypatch):
    """Naming the levels was not enough: asked for a restaurant dinner with only a
    gown in the wardrobe, the model answered `formal` and suggested the gown. The
    level has to be decided from the occasion, not from what happens to be owned."""
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")
    seen = {}
    monkeypatch.setattr(client, "_chat",
                        lambda messages, **kw: seen.setdefault("system", messages[0]["content"]) and "{}" or "{}")

    client.generate_outfit("akşam yemeği", [], [], "tr")
    system = seen["system"]

    assert "This is the answer for MOST evenings out" in system
    assert "what the wardrobe happens to contain must not raise the level" in system
