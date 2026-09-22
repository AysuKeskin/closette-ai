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
    _MAX_OUTPUT,
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


def test_unreadable_bytes_are_refused_rather_than_passed_on():
    # This used to assert the opposite, and that is how a HEIC photo reached the
    # provider wearing a JPEG label: the 400 that came back read as a bad key.
    with pytest.raises(ValueError, match="could not decode"):
        _shrink(b"not-an-image")


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

    assert look == {"itemIds": ["a", "b"], "title": "Soft evening",
                    "rationale": "Because it works.", "formality": "", "season": ""}


def test_the_formality_the_model_decided_reaches_the_caller(set_env, monkeypatch):
    """The backend keeps gowns out of everything but a formal occasion, and it can
    only do that if the model's own judgement comes back with the picks."""
    set_env(VLM_API_KEY="test-key")
    reply = ('{"plan":{"formality":"Formal","season":"summer","needs":"a gown"},'
             '"itemIds":["a"],"title":"Gala","rationale":"Black tie."}')

    look = _stylist(monkeypatch, reply).generate_outfit("gala", [], [])

    assert look["formality"] == "formal"
    assert look["season"] == "summer"
    assert "plan" not in look


def test_stylist_snake_case_ids_are_accepted(set_env, monkeypatch):
    # Models drift between itemIds and item_ids; both must land.
    set_env(VLM_API_KEY="test-key")

    look = _stylist(monkeypatch, '{"item_ids":["a"]}').generate_outfit("dinner", [], [])

    assert look["itemIds"] == ["a"]
    # Blank, not an English default — the backend supplies the localized title.
    assert look["title"] == ""


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


def test_shrink_converts_a_real_image_to_jpeg():
    from app.providers.vlm_api import _shrink

    out = _shrink(jpeg_bytes())
    assert out.startswith(b"\xff\xd8")  # JPEG magic number


def _chat_client(set_env, monkeypatch, body: dict):
    """A client whose HTTP round trip is replaced by one canned provider reply."""
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")

    sent: dict = {}

    class FakeResponse:
        status_code = 200

        def json(self):
            return body

        def raise_for_status(self):
            return None

    class FakeClient:
        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

        def post(self, url, json=None, headers=None):
            sent.update(json or {})
            return FakeResponse()

    monkeypatch.setattr("app.providers.vlm_api.httpx.Client", lambda timeout=60: FakeClient())
    return client, sent


def test_every_call_caps_its_own_output(set_env, monkeypatch):
    """
    An uncapped answer is billed by the token however long it runs.

    Each of these flows returns a small JSON object, so a long completion means
    the model lost the format — and the cap turns that into a bounded, visible
    failure rather than a bill.
    """
    client, sent = _chat_client(set_env, monkeypatch, {
        "choices": [{"message": {"content": "{}"}, "finish_reason": "stop"}],
        "usage": {"prompt_tokens": 1200, "completion_tokens": 90, "total_tokens": 1290},
    })

    client.generate_outfit("dinner", [], [], "en")

    assert sent["max_tokens"] == _MAX_OUTPUT["generate_outfit"]


def test_the_tokens_a_call_spent_are_logged_against_its_operation(set_env, monkeypatch, caplog):
    """
    Without this line there is no answer to "what does one outfit cost", and no
    paid allowance can be priced on a number nobody measured.
    """
    client, _ = _chat_client(set_env, monkeypatch, {
        "choices": [{"message": {"content": "{}"}, "finish_reason": "stop"}],
        "usage": {"prompt_tokens": 6000, "completion_tokens": 1200, "total_tokens": 7200},
    })

    with caplog.at_level(logging.INFO, logger="app.providers.vlm_api"):
        client.generate_outfit("gala", [], [], "en")

    assert "vlm_usage" in caplog.text
    assert "operation=generate_outfit" in caplog.text
    assert "prompt_tokens=6000" in caplog.text
    assert "completion_tokens=1200" in caplog.text


def test_an_answer_cut_off_by_the_cap_says_so(set_env, monkeypatch, caplog):
    # Truncated JSON otherwise reads as the model failing, and the real cause —
    # our own ceiling — is invisible.
    client, _ = _chat_client(set_env, monkeypatch, {
        "choices": [{"message": {"content": '{"itemIds": ['}, "finish_reason": "length"}],
        "usage": {"prompt_tokens": 100, "completion_tokens": 500, "total_tokens": 600},
    })

    with caplog.at_level(logging.WARNING, logger="app.providers.vlm_api"):
        client.generate_outfit("dinner", [], [], "en")

    assert "hit the output cap" in caplog.text


def _failing_client(set_env, monkeypatch, status: int, body: str):
    set_env(VLM_API_KEY="test-key")
    client = OpenAICompatibleVLM("openai")

    class FakeResponse:
        status_code = status
        text = body

        def raise_for_status(self):
            raise RuntimeError(f"HTTP {status}")

    class FakeClient:
        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

        def post(self, url, json=None, headers=None):
            return FakeResponse()

    monkeypatch.setattr("app.providers.vlm_api.httpx.Client", lambda timeout=60: FakeClient())
    return client


def test_an_empty_provider_account_is_reported_as_needing_a_human(set_env, monkeypatch, caplog):
    """
    Running out of credit stops every AI feature and fixes itself never.

    It used to be logged exactly like a momentary blip, so the app would quietly
    stop working until somebody happened to look. The line has to say what to do.
    """
    client = _failing_client(set_env, monkeypatch, 429,
                             '{"error":{"code":"insufficient_quota"}}')

    with caplog.at_level(logging.WARNING, logger="app.providers.vlm_api"):
        with pytest.raises(RuntimeError):
            client.generate_outfit("dinner", [], [], "en")

    assert any(r.levelname == "ERROR" for r in caplog.records)
    assert "out of credit" in caplog.text


def test_a_rejected_key_is_reported_as_needing_a_human(set_env, monkeypatch, caplog):
    client = _failing_client(set_env, monkeypatch, 401, '{"error":{"code":"invalid_api_key"}}')

    with caplog.at_level(logging.WARNING, logger="app.providers.vlm_api"):
        with pytest.raises(RuntimeError):
            client.generate_outfit("dinner", [], [], "en")

    assert any(r.levelname == "ERROR" for r in caplog.records)
    assert "credentials" in caplog.text


def test_ordinary_throttling_stays_a_warning(set_env, monkeypatch, caplog):
    # This one does clear on its own, so waking somebody for it trains them to
    # ignore the ones that do not.
    client = _failing_client(set_env, monkeypatch, 429, '{"error":{"code":"rate_limit_exceeded"}}')

    with caplog.at_level(logging.WARNING, logger="app.providers.vlm_api"):
        with pytest.raises(RuntimeError):
            client.generate_outfit("dinner", [], [], "en")

    assert not any(r.levelname == "ERROR" for r in caplog.records)
    assert "clear on its own" in caplog.text
