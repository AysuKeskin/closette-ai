from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration for the AI service.

    Each layer picks its own backend, so we only spend model compute where it's
    actually needed (NFR-13). Defaults are all no-key / no-GPU so the whole
    service runs offline:

      - ``ai_provider``  → the VLM (attributes · OCR · reasoning). mock | qwen | openai | kimi
      - ``seg_provider`` → background removal. none (passthrough) | birefnet
      - ``embed_provider`` → fashion similarity vectors. mock | fashionsiglip

    Colour extraction, normalisation and scoring are NOT here — they're classic
    code, never a provider.
    """

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # VLM — only used for things a model is actually needed for.
    ai_provider: str = "mock"          # mock | qwen | openai | kimi
    qwen_api_key: str | None = None
    openai_api_key: str | None = None
    kimi_api_key: str | None = None

    # Classic-tool seams (default to no model).
    seg_provider: str = "none"         # none | birefnet
    embed_provider: str = "mock"       # mock | fashionsiglip

    # Optional: route low-confidence VLM results to a paid model (off by default).
    vlm_fallback_provider: str | None = None   # e.g. "openai" | "kimi"
    vlm_confidence_threshold: float = 0.45

    ai_service_port: int = 8000


@lru_cache
def get_settings() -> Settings:
    return Settings()
