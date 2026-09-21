from functools import lru_cache

from pydantic import model_validator
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

    app_env: str = "development"

    @model_validator(mode="after")
    def production_settings(self):
        if self.app_env == "production":
            allowed = {"openai", "openrouter", "dashscope", "qwen", "moonshot", "kimi"}
            if self.ai_provider not in allowed:
                raise ValueError("Production requires an explicit supported AI provider")
            if self.vlm_fallback_to_mock:
                raise ValueError("Disable VLM_FALLBACK_TO_MOCK in production")
            if not self.vlm_model:
                raise ValueError("Pin VLM_MODEL in production")
            if not (self.vlm_api_key or self.openai_api_key or self.qwen_api_key or self.kimi_api_key):
                raise ValueError("Production requires an AI API key")
            if self.vlm_base_url and not self.vlm_base_url.startswith("https://"):
                raise ValueError("VLM_BASE_URL must use HTTPS in production")
        return self

    # VLM — only used for things a model is actually needed for.
    # All non-mock providers speak the OpenAI chat-completions schema, so one
    # client covers OpenAI · OpenRouter · DashScope(Qwen) · Moonshot(Kimi).
    ai_provider: str = "mock"          # mock | openai | openrouter | dashscope | qwen | moonshot | kimi
    qwen_api_key: str | None = None
    openai_api_key: str | None = None
    kimi_api_key: str | None = None
    # Generic overrides (win over per-provider defaults).
    vlm_api_key: str | None = None
    vlm_base_url: str | None = None
    vlm_model: str | None = None
    # On any VLM error, fall back to the deterministic mock so the app never breaks.
    vlm_fallback_to_mock: bool = True

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
