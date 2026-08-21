"""Production VLM provider — OpenAI-compatible chat/vision API over httpx.

One client covers OpenAI, OpenRouter, Alibaba DashScope (Qwen-VL) and Moonshot
(Kimi), since they all speak the OpenAI chat-completions schema. Used ONLY for
what a model is actually needed for: clothing/beauty attributes, OCR/brand, and
ingredient explanations. Colour, similarity and scoring stay as classic code.
"""
from __future__ import annotations

import base64
import io
import json
import logging

import httpx
from PIL import Image

try:  # iPhone photos are often HEIC — register the opener so PIL can read them.
    from pillow_heif import register_heif_opener

    register_heif_opener()
except Exception:  # noqa: BLE001
    pass

from app.core.config import get_settings
from app.providers.base import AIProvider
from app.schemas.analysis import BeautyAnalysis, ClothingAnalysis, IngredientExplanation

log = logging.getLogger(__name__)

# provider name -> (default base_url, default vision model). Models drift — set
# VLM_MODEL to pin an exact one.
PROVIDER_DEFAULTS = {
    "openai": ("https://api.openai.com/v1", "gpt-4o-mini"),
    "openrouter": ("https://openrouter.ai/api/v1", "qwen/qwen-2.5-vl-72b-instruct"),
    "dashscope": ("https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "qwen-vl-max"),
    "qwen": ("https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "qwen-vl-max"),
    "moonshot": ("https://api.moonshot.ai/v1", "moonshot-v1-8k-vision-preview"),
    "kimi": ("https://api.moonshot.ai/v1", "moonshot-v1-8k-vision-preview"),
}

_CLOTHING_SYS = (
    "You are a fashion cataloguing assistant. Look at the clothing item and reply with "
    "ONLY a JSON object, no prose, with keys: "
    "category (one of: top, bottom, dress, outerwear, shoes, bag, accessory, jewelry), "
    "subcategory (short free text, e.g. 'cropped cardigan'), "
    "pattern (e.g. solid, striped, floral, checked), "
    "styles (array of 1-3 style words, e.g. minimal, feminine, classic), "
    "seasons (array from: spring, summer, fall, winter), "
    "confidence (number 0-1). Do not include colours."
)

_STYLIST_SYS = (
    "You are a personal stylist. You are given an occasion and the user's OWNED wardrobe "
    "items (each with an id). Compose ONE complete, cohesive outfit using ONLY these items — "
    "never invent items or ids. Rules: pick at most one top AND one bottom, OR a single dress "
    "(a dress replaces top+bottom); then optionally add one each of shoes, outerwear, bag, "
    "jewelry, accessory IF they suit the occasion and colours harmonise. Prefer items matching "
    "the stated style preferences and the season/formality of the occasion. Reply with ONLY a "
    "JSON object: {\"itemIds\": [ids in wear order], \"title\": short catchy name, "
    "\"rationale\": one or two sentences on why this works for the occasion}."
)

_BUY_SYS = (
    "You are an honest shopping advisor for a personal wardrobe app. Given a candidate item the "
    "user is thinking of buying, the similar items they ALREADY own, and computed compatibility "
    "scores, decide if it's worth buying. Favour their wardrobe's versatility over impulse: if "
    "they already own similar pieces, lean 'skip'; if it fills a gap and matches their style/"
    "colours, lean 'buy'. Reply with ONLY JSON: {\"verdict\": \"buy\" | \"maybe\" | \"skip\", "
    "\"explanation\": one or two honest, friendly sentences}."
)

_BEAUTY_SYS = (
    "You are a beauty-product cataloguing assistant. Read any visible text/branding and reply "
    "with ONLY a JSON object, no prose, with keys: "
    "brand (string, '' if unknown), productName (string), "
    "category (one of: skincare, makeup, haircare, bodycare, perfume, nails), "
    "confidence (number 0-1)."
)


class OpenAICompatibleVLM(AIProvider):
    def __init__(self, provider: str) -> None:
        s = get_settings()
        base_default, model_default = PROVIDER_DEFAULTS.get(provider, PROVIDER_DEFAULTS["openai"])
        self.base_url = (s.vlm_base_url or base_default).rstrip("/")
        self.model = s.vlm_model or model_default
        self.api_key = (
            s.vlm_api_key
            or (s.openai_api_key if provider == "openai" else None)
            or (s.qwen_api_key if provider in ("qwen", "dashscope") else None)
            or (s.kimi_api_key if provider in ("kimi", "moonshot") else None)
        )
        if not self.api_key:
            raise RuntimeError(
                f"No API key for AI_PROVIDER={provider}. Set VLM_API_KEY (or the "
                "provider-specific key) in the AI service environment."
            )
        self.provider = provider

    # ---- public API ----
    def analyze_clothing(self, image: bytes, filename: str) -> ClothingAnalysis:
        data = self._vision_json(_CLOTHING_SYS, "Analyze this clothing item.", image, filename)
        return ClothingAnalysis(
            category=str(data.get("category", "top")).lower(),
            subcategory=str(data.get("subcategory", "")),
            colors=[],  # colours come from the classic-code pipeline, not the VLM
            pattern=str(data.get("pattern", "")),
            styles=[str(x) for x in data.get("styles", []) if x][:3],
            seasons=[str(x).lower() for x in data.get("seasons", []) if x],
            confidence=_as_float(data.get("confidence"), 0.8),
        )

    def analyze_beauty(self, image: bytes, filename: str) -> BeautyAnalysis:
        data = self._vision_json(_BEAUTY_SYS, "Identify this beauty product.", image, filename)
        return BeautyAnalysis(
            brand=str(data.get("brand", "")),
            product_name=str(data.get("productName") or data.get("product_name") or ""),
            category=str(data.get("category", "skincare")).lower(),
            confidence=_as_float(data.get("confidence"), 0.8),
        )

    def explain_ingredient(self, name: str) -> IngredientExplanation:
        content = self._chat([
            {"role": "system", "content": "You explain cosmetic ingredients in one friendly, "
                                          "non-medical sentence for a beauty app."},
            {"role": "user", "content": f"Explain the skincare/cosmetic ingredient: {name}"},
        ])
        return IngredientExplanation(name=name, explanation=content.strip())

    def generate_outfit(self, occasion: str, items: list[dict], preferences: list[str]) -> dict:
        context = {
            "occasion": occasion,
            "preferences": preferences or [],
            "wardrobe": items,  # the RETRIEVED context — only the user's own pieces
        }
        content = self._chat(
            [
                {"role": "system", "content": _STYLIST_SYS},
                {"role": "user", "content": json.dumps(context, ensure_ascii=False)},
            ],
            json_mode=True,
        )
        data = _extract_json(content)
        ids = data.get("itemIds") or data.get("item_ids") or []
        return {
            "itemIds": [str(x) for x in ids],
            "title": str(data.get("title") or "Your look"),
            "rationale": str(data.get("rationale") or ""),
        }

    def buy_advice(self, candidate: dict, matches: list[dict], scores: dict) -> dict:
        context = {"candidate": candidate, "ownedSimilar": matches, "scores": scores}
        content = self._chat(
            [
                {"role": "system", "content": _BUY_SYS},
                {"role": "user", "content": json.dumps(context, ensure_ascii=False)},
            ],
            json_mode=True,
        )
        data = _extract_json(content)
        verdict = str(data.get("verdict") or "maybe").lower()
        if verdict not in ("buy", "maybe", "skip"):
            verdict = "maybe"
        return {"verdict": verdict, "explanation": str(data.get("explanation") or "")}

    # ---- internals ----
    def _vision_json(self, system: str, prompt: str, image: bytes, filename: str) -> dict:
        # Cost lever: the VLM only needs to *recognise* the item, so we send a
        # small JPEG at low detail. Colour/similarity use the full image locally.
        small = _shrink(image)
        data_url = f"data:image/jpeg;base64,{base64.b64encode(small).decode()}"
        image_part = {"type": "image_url", "image_url": {"url": data_url}}
        if self.provider in ("openai", "openrouter"):
            image_part["image_url"]["detail"] = "low"  # ~85 tokens instead of hundreds
        content = self._chat(
            [
                {"role": "system", "content": system},
                {"role": "user", "content": [{"type": "text", "text": prompt}, image_part]},
            ],
            json_mode=True,
        )
        return _extract_json(content)

    def _chat(self, messages: list, json_mode: bool = False) -> str:
        payload: dict = {"model": self.model, "messages": messages, "temperature": 0}
        if json_mode and self.provider in ("openai", "openrouter"):
            payload["response_format"] = {"type": "json_object"}
        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        if self.provider == "openrouter":
            headers["HTTP-Referer"] = "https://closette.ai"
            headers["X-Title"] = "Closette"
        with httpx.Client(timeout=60) as client:
            r = client.post(f"{self.base_url}/chat/completions", json=payload, headers=headers)
            if r.status_code >= 400:
                log.warning("VLM %s HTTP %s: %s", self.model, r.status_code, r.text[:600])
            r.raise_for_status()
            return r.json()["choices"][0]["message"]["content"]


def _shrink(image: bytes, max_side: int = 512) -> bytes:
    """Downscale to a small JPEG to minimise vision-token cost."""
    try:
        img = Image.open(io.BytesIO(image)).convert("RGB")
        img.thumbnail((max_side, max_side))
        out = io.BytesIO()
        img.save(out, "JPEG", quality=85)
        return out.getvalue()
    except Exception:
        return image


def _extract_json(text: str) -> dict:
    text = (text or "").strip()
    if text.startswith("```"):
        text = text.strip("`")
        text = text[text.find("{"):]
    try:
        return json.loads(text)
    except Exception:
        start, end = text.find("{"), text.rfind("}")
        if 0 <= start < end:
            try:
                return json.loads(text[start:end + 1])
            except Exception:
                pass
    log.warning("VLM returned non-JSON content: %r", text[:200])
    return {}


def _as_float(v, default: float) -> float:
    try:
        return float(v)
    except (TypeError, ValueError):
        return default
