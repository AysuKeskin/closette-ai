"""Parsing helpers around the real VLM call. These run without any network or
API key — they cover the ways a model reply can be malformed."""
import io
import logging

import pytest
from PIL import Image

from app.providers.vlm_api import (
    OpenAICompatibleVLM,
    PROVIDER_DEFAULTS,
    _as_float,
    _extract_json,
    _shrink,
)

from tests.images import jpeg_bytes


def test_plain_json_object_is_parsed():
    assert _extract_json('{"category": "top", "confidence": 0.8}') == {
        "category": "top", "confidence": 0.8
    }


def test_fenced_code_block_is_unwrapped():
    assert _extract_json('```json\n{"category": "dress"}\n```') == {"category": "dress"}


def test_object_embedded_in_prose_is_recovered():
    reply = 'Sure! Here is the analysis: {"category": "shoes"} Hope that helps.'

    assert _extract_json(reply) == {"category": "shoes"}


def test_non_json_reply_degrades_to_an_empty_dict(caplog):
    with caplog.at_level(logging.WARNING, logger="app.providers.vlm_api"):
        assert _extract_json("I'm not able to analyse this image.") == {}

    # The caller silently uses defaults, so the log line is the only trace.
    assert "non-JSON" in caplog.text


def test_empty_reply_degrades_to_an_empty_dict():
    assert _extract_json("") == {}
    assert _extract_json(None) == {}


@pytest.mark.parametrize("value", [None, "", "high", {}])
def test_unparsable_confidence_falls_back_to_the_default(value):
    assert _as_float(value, 0.8) == 0.8


@pytest.mark.parametrize("value,expected", [(0.42, 0.42), ("0.42", 0.42), (1, 1.0)])
def test_numeric_confidence_is_kept(value, expected):
    assert _as_float(value, 0.8) == expected


def test_image_is_shrunk_to_a_small_jpeg():
    # Vision tokens are billed by pixels; the long edge must be bounded.
    shrunk = _shrink(jpeg_bytes(width=1024, height=512), max_side=256)
    img = Image.open(io.BytesIO(shrunk))

    assert img.format == "JPEG"
    assert max(img.size) == 256
    assert img.size == (256, 128)


def test_unreadable_bytes_pass_through_shrink_untouched():
    assert _shrink(b"not-an-image") == b"not-an-image"


def test_client_refuses_to_start_without_an_api_key(set_env):
    set_env(VLM_API_KEY=None, OPENAI_API_KEY=None, QWEN_API_KEY=None, KIMI_API_KEY=None)

    with pytest.raises(RuntimeError, match="No API key"):
        OpenAICompatibleVLM("openai")


def test_provider_specific_key_and_defaults_are_picked_up(set_env):
    set_env(AI_PROVIDER="qwen", VLM_API_KEY=None, OPENAI_API_KEY=None,
            QWEN_API_KEY="qwen-key", VLM_BASE_URL=None, VLM_MODEL=None)

    client = OpenAICompatibleVLM("qwen")

    assert client.api_key == "qwen-key"
    assert (client.base_url, client.model) == PROVIDER_DEFAULTS["qwen"]


def test_generic_overrides_win_over_provider_defaults(set_env):
    set_env(VLM_API_KEY="generic-key", VLM_BASE_URL="http://localhost:11434/v1/",
            VLM_MODEL="my-vision-model", OPENAI_API_KEY="ignored")

    client = OpenAICompatibleVLM("openai")

    assert client.api_key == "generic-key"
    assert client.base_url == "http://localhost:11434/v1"  # trailing slash trimmed
    assert client.model == "my-vision-model"


def _stylist(monkeypatch, reply: str) -> OpenAICompatibleVLM:
    """A VLM client whose chat call is replaced by a canned model reply."""
    client = OpenAICompatibleVLM("openai")
    monkeypatch.setattr(client, "_chat", lambda *args, **kwargs: reply)
    return client


def test_stylist_reply_is_mapped_to_the_outfit_contract(set_env, monkeypatch):
    set_env(VLM_API_KEY="test-key")
    reply = '{"itemIds":["a","b"],"title":"Soft evening","rationale":"Because it works."}'

    look = _stylist(monkeypatch, reply).generate_outfit("dinner", [], [])

    assert look == {"itemIds": ["a", "b"], "title": "Soft evening", "rationale": "Because it works."}


def test_stylist_snake_case_ids_are_accepted(set_env, monkeypatch):
    # Models drift between itemIds and item_ids; both must land.
    set_env(VLM_API_KEY="test-key")

    look = _stylist(monkeypatch, '{"item_ids":["a"]}').generate_outfit("dinner", [], [])

    assert look["itemIds"] == ["a"]
    assert look["title"] == "Your look"  # default, not empty


def test_stylist_ids_are_coerced_to_strings(set_env, monkeypatch):
    set_env(VLM_API_KEY="test-key")

    look = _stylist(monkeypatch, '{"itemIds":[1,2]}').generate_outfit("dinner", [], [])

    assert look["itemIds"] == ["1", "2"]


def test_unusable_stylist_reply_yields_an_empty_look_for_the_caller_to_reject(set_env, monkeypatch):
    # The backend treats an empty itemIds list as "AI had nothing" and falls
    # back to its own composer, so this must not raise.
    set_env(VLM_API_KEY="test-key")

    look = _stylist(monkeypatch, "I cannot help with that.").generate_outfit("dinner", [], [])

    assert look["itemIds"] == []
