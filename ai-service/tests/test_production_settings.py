import pytest
from pydantic import ValidationError
from app.core.config import Settings
from app.providers import get_provider


def test_production_refuses_the_development_defaults():
    with pytest.raises(ValidationError, match="explicit supported AI provider"):
        Settings(app_env="production")


def test_production_refuses_mock_fallback_even_with_a_real_provider():
    with pytest.raises(ValidationError, match="Disable VLM_FALLBACK_TO_MOCK"):
        Settings(app_env="production", ai_provider="openai", vlm_api_key="test", vlm_model="test")


def test_production_accepts_explicit_provider_and_no_mock_fallback():
    settings = Settings(app_env="production", ai_provider="openai", vlm_api_key="test",
                        vlm_model="test-model", vlm_fallback_to_mock=False)
    assert settings.ai_provider == "openai"


def test_initialization_failure_does_not_silently_become_mock_when_disabled(set_env):
    set_env(AI_PROVIDER="openai", VLM_FALLBACK_TO_MOCK="false")
    with pytest.raises(Exception):
        get_provider()
