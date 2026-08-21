import pytest

from app.core.config import get_settings

# Everything Settings reads from the environment. Cleared before each test so a
# developer's exported keys can never change what the suite asserts.
_SETTINGS_ENV = (
    "AI_PROVIDER",
    "SEG_PROVIDER",
    "EMBED_PROVIDER",
    "VLM_API_KEY",
    "VLM_BASE_URL",
    "VLM_MODEL",
    "VLM_FALLBACK_TO_MOCK",
    "VLM_FALLBACK_PROVIDER",
    "VLM_CONFIDENCE_THRESHOLD",
    "OPENAI_API_KEY",
    "QWEN_API_KEY",
    "KIMI_API_KEY",
    "AI_SERVICE_PORT",
)


@pytest.fixture(autouse=True)
def isolated_settings(monkeypatch, tmp_path):
    """Run every test against the built-in defaults: mock VLM, no segmentation,
    mock embedder.

    Two things would otherwise leak a real provider into the suite: exported
    environment variables, and ``ai-service/.env``, which pydantic-settings
    loads relative to the working directory. So the vars are unset and the test
    runs from an empty directory. Without this, a developer with a live API key
    gets slow, paid, non-deterministic runs that disagree with CI.
    """
    for name in _SETTINGS_ENV:
        monkeypatch.delenv(name, raising=False)
    monkeypatch.chdir(tmp_path)
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


@pytest.fixture
def set_env(monkeypatch):
    """Override AI-service configuration for one test.

    ``Settings`` is cached with ``lru_cache``, so the cache has to be dropped
    after every change. Passing ``None`` unsets a variable, which is how the
    "no key configured" paths are exercised.
    """
    def _set(**env: str | None) -> None:
        for key, value in env.items():
            if value is None:
                monkeypatch.delenv(key, raising=False)
            else:
                monkeypatch.setenv(key, value)
        get_settings.cache_clear()

    return _set
