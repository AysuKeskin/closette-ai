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
from app.core.language import prompt_instruction, subcategory_instruction
from app.providers.base import AIProvider
from app.schemas.analysis import BeautyAnalysis, ClothingAnalysis, IngredientExplanation

log = logging.getLogger(__name__)

# Output caps, per flow. Every one of these answers is a small JSON object, so a
# long completion means the model is repeating itself or has lost the format —
# and either way it is billed by the token. The cap turns a runaway answer into a
# bounded, visible failure instead of a bill.
_DEFAULT_MAX_OUTPUT = 400
_MAX_OUTPUT = {
    "analyze_clothing": 300,
    "parse_clothing": 300,
    "analyze_beauty": 300,
    "extract_ingredients": 700,   # an ingredient list is genuinely long
    "explain_ingredient": 300,
    "generate_outfit": 500,       # ids, a title, a rationale and the plan
    "buy_advice": 300,
}

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

# The app stores styles as a closed vocabulary: it translates them for display and
# the stylist reasons over them, so an invented word is a word nothing understands.
# `formal` in particular is what marks an evening gown, and the backend keeps those
# out of everyday occasions — a gown catalogued as a summer dress defeats that.
_STYLE_VOCABULARY = (
    "styles (array of 1-3 words, CHOSEN ONLY from: minimal, classic, elegant, casual, chic, "
    "edgy, boho, romantic, sporty, streetwear, preppy, feminine, timeless, trendy, oversized, "
    "fitted, vintage, cottagecore, formal. Never invent a word outside this list. "
    "`formal` is not an alternative to `elegant`, it is a REQUIRED FLAG: whenever the piece "
    "is dressy enough for a black-tie or ceremonial event, `formal` MUST appear in the array, and "
    "you may list elegant alongside it. That covers a floor-length or occasion gown (Turkish: "
    "abiye, nişanlık, gelinlik), a sequinned or beaded evening dress, a tuxedo (smokin), and a "
    "formal ceremonial suit. It does NOT cover a pretty summer dress, a mini party dress, a smart "
    "blazer or a silk blouse; those are elegant, chic or classic and must NOT carry `formal`. "
    "The app hides `formal` pieces from everyday occasions, so a gown missing this flag gets "
    "suggested for a coffee, and a blouse carrying it never gets suggested at all), "
)

_CLOTHING_SYS = (
    "You are a fashion cataloguing assistant. Look at the clothing item and reply with "
    "ONLY a JSON object, no prose, with keys: "
    "category (one of: top, bottom, dress, outerwear, shoes, bag, accessory, jewelry), "
    "subcategory (short free text, e.g. 'cropped cardigan'), "
    "pattern (one of: solid, striped, floral, checked), "
    + _STYLE_VOCABULARY +
    "seasons (array from: spring, summer, fall, winter), "
    "confidence (number 0-1). Do not include colours."
)

_INGREDIENTS_OCR_SYS = (
    "You read the INGREDIENTS list printed on a beauty/skincare product from the photo. "
    "Reply with ONLY a JSON object {\"ingredients\": [...]} where the array holds each ingredient "
    "as written, in order, one entry per ingredient. Do not translate, do not invent, do not add "
    "commentary. If no ingredient list is visible, return {\"ingredients\": []}."
)

_PARSE_SYS = (
    "You parse a shopper's free-text description of ONE clothing item into JSON. Reply with "
    "ONLY a JSON object, no prose, with keys: "
    "category (one of: top, bottom, dress, outerwear, shoes, bag, accessory, jewelry), "
    "subcategory (short free text, e.g. 'oversized blazer'), "
    "colors (array of colour words mentioned, lowercase, e.g. ['beige']), "
    + _STYLE_VOCABULARY +
    "pattern (one of: solid, striped, floral, checked), "
    "seasons (array from: spring, summer, fall, winter — when the piece is actually wearable. "
    "A thick knit or a wool coat is fall+winter; linen, a swimsuit or sandals are summer; a "
    "trench or a light cardigan is spring+fall. Use all four only for something genuinely "
    "year-round), "
    "confidence (number 0-1). Infer sensibly from the words; use [] when unsure. "
    "If the text does NOT describe a clothing/fashion item (gibberish, a food, a question, etc.), "
    "return category \"unknown\", empty colors and styles, and confidence 0 — do NOT guess."
)

_STYLIST_SYS = (
    "You are a personal stylist. You are given an occasion and the user's OWNED wardrobe "
    "items (each with an id). Compose ONE complete, cohesive outfit using ONLY these items — "
    "never invent items or ids.\n"
    "`dominantColor`, when a piece has one, is the colour covering most of it, measured from "
    "the photograph. Harmonise around those rather than around the full colour list: a white "
    "shirt with a navy collar lists both, but it wears as white. A piece without one is "
    "genuinely multicoloured, so keep the rest of the look quiet around it.\n"
    "THINK FIRST, in this order, and put it in the \"plan\" field before choosing anything:\n"
    "1. How formal is this occasion? Answer with one of: casual, smart, formal.\n"
    "   - casual: coffee, groceries, a walk, the beach, a picnic, the cinema, a brunch.\n"
    "   - smart: a restaurant dinner, a date, a birthday, a party, a job interview, the office, "
    "a family gathering. This is the answer for MOST evenings out.\n"
    "   - formal: only black-tie or ceremonial — a wedding, a gala, a ball, an award ceremony, "
    "an engagement. Decide this from the OCCASION alone; what the wardrobe happens to contain "
    "must not raise the level, and owning nothing suitable is a reason to say so in the "
    "rationale, never a reason to call a dinner formal.\n"
    "2. What season does it imply? Answer with one of: spring, summer, fall, winter.\n"
    "3. What does it physically involve? Sand and water, rain, snow, a lot of walking, "
    "exercise, sitting indoors? Every piece has to be PRACTICAL for that, not merely the "
    "right level of dressy: no boots or closed shoes for a beach or a pool, no sandals or "
    "heels for snow, rain or a hike, no delicate fabrics for the gym.\n"
    "4. What does the look need to work, and which owned pieces are the strongest fit?\n"
    "Formality decides the outfit, not the season. A wedding, a job interview or a dinner is NOT "
    "the same outfit as a beach day or a coffee, even in identical weather. Apply it MECHANICALLY "
    "against each item's own style words:\n"
    "- formal or smart: use only items styled elegant, classic, timeless, chic, formal, minimal or "
    "romantic. Items styled sporty, boho, casual, streetwear or edgy are FORBIDDEN, including bags "
    "and shoes. A straw bag or a sneaker in a wedding look is a wrong answer.\n"
    "- casual: prefer casual, sporty, boho, streetwear, minimal. Heels and tailoring are wrong here.\n"
    "Season is mechanical in the same way, against each item's own `seasons`: never put a "
    "summer-only piece into a winter occasion or a winter-only piece into a summer one. Spring "
    "and fall pieces work in either shoulder season. A floral summer dress in the snow is a "
    "wrong answer even under a coat.\n"
    "A piece made for one setting does not transfer to another: swimwear belongs at a beach or "
    "pool and nowhere else, gym clothes belong at exercise, and neither is an everyday outfit.\n"
    "Then compose: at most one top AND one bottom, OR a single dress (a dress replaces "
    "top+bottom); then optionally add one each of shoes, outerwear, bag, jewelry, accessory IF "
    "they suit that formality and the colours harmonise. The look MUST include exactly one pair of "
    "shoes whenever the wardrobe contains any that fit the formality; a look without shoes is "
    "incomplete. Prefer items matching the stated style preferences.\n"
    "An item styled `formal` is an evening gown. Use one ONLY for a genuinely special event: a "
    "wedding, a gala, a ball, a formal ceremony, a black-tie dinner. A restaurant dinner, a party, "
    "a date or a birthday is NOT one of those. Overdressing is as wrong as underdressing.\n"
    "The context may carry `alreadySuggested`, the look the user was just shown and did not want. "
    "Compose a genuinely DIFFERENT one: change the main pieces, not only the bag or the shoes. "
    "Reuse an item from it only when the wardrobe leaves no alternative for that slot.\n"
    "When several combinations work equally well, pick a different one rather than always the "
    "same obvious default — the user asks repeatedly and wants to see their wardrobe, not one look.\n"
    "A real occasion ALWAYS gets an outfit: if the wardrobe suits it poorly, still compose the "
    "closest workable look from what is there and say plainly in the rationale what is missing. "
    "Return an EMPTY itemIds array ONLY when the input is not an occasion at all — gibberish, a "
    "random word, or an empty string — and then say you didn't understand and ask for a clearer "
    "occasion. Never refuse a genuine occasion such as a beach day, a wedding or a job interview.\n"
    "Reply with ONLY a JSON object, keys in this order: "
    "{\"plan\": {\"formality\": \"casual|smart|formal\", "
    "\"season\": \"spring|summer|fall|winter\", \"setting\": short text, "
    "\"needs\": short text}, \"itemIds\": [ids in wear order], \"title\": short catchy name, "
    "\"rationale\": one or two sentences on why this works for the occasion}."
)

_BUY_SYS = (
    "You are an honest shopping advisor for a personal wardrobe app. Given a candidate item the "
    "user is thinking of buying, the similar items they ALREADY own, and computed compatibility "
    "scores, decide if it's worth buying. Favour their wardrobe's versatility over impulse: if "
    "they already own similar pieces, lean 'skip'; if it fills a gap and matches their style/"
    "colours, lean 'buy'. Where an owned piece carries `colorShares` (\"name:percent\", measured "
    "from its photo), judge by what it actually reads as: three trousers that merely mention navy "
    "is not the same as three that ARE navy, and only the second makes a fourth pointless. "
    "Reply with ONLY JSON: {\"verdict\": \"buy\" | \"maybe\" | \"skip\", "
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
    def analyze_clothing(self, image: bytes, filename: str, lang: str = "en") -> ClothingAnalysis:
        system = _CLOTHING_SYS + " " + subcategory_instruction(lang)
        data = self._vision_json(system, "Analyze this clothing item.", image, filename,
                                 operation="analyze_clothing")
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
        data = self._vision_json(_BEAUTY_SYS, "Identify this beauty product.", image, filename,
                                 operation="analyze_beauty")
        return BeautyAnalysis(
            brand=str(data.get("brand", "")),
            product_name=str(data.get("productName") or data.get("product_name") or ""),
            category=str(data.get("category", "skincare")).lower(),
            confidence=_as_float(data.get("confidence"), 0.8),
        )

    def extract_ingredients(self, image: bytes, filename: str) -> list[str]:
        data = self._vision_json(_INGREDIENTS_OCR_SYS, "Read the ingredient list.", image, filename,
                                 operation="extract_ingredients")
        items = data.get("ingredients", [])
        return [str(x).strip() for x in items if str(x).strip()][:60]

    def parse_clothing(self, description: str, lang: str = "en") -> ClothingAnalysis:
        content = self._chat(
            [
                {"role": "system", "content": _PARSE_SYS + " " + subcategory_instruction(lang)},
                {"role": "user", "content": description},
            ],
            json_mode=True,
            operation="parse_clothing",
            max_output_tokens=_MAX_OUTPUT["parse_clothing"],
        )
        data = _extract_json(content)
        return ClothingAnalysis(
            category=str(data.get("category", "unknown")).lower(),  # no guess when absent
            subcategory=str(data.get("subcategory", "")),
            colors=[str(c).lower() for c in data.get("colors", []) if c][:4],
            pattern=str(data.get("pattern", "")),
            styles=[str(x).lower() for x in data.get("styles", []) if x][:3],
            seasons=[str(x).lower() for x in data.get("seasons", []) if x],
            confidence=_as_float(data.get("confidence"), 0.6),
        )

    def explain_ingredient(self, name: str, lang: str = "en") -> IngredientExplanation:
        content = self._chat([
            {"role": "system", "content": (
                "You explain a cosmetic/skincare ingredient in ONE friendly, non-medical sentence "
                "for a beauty app. Always answer with a definition of the given term. Never ask the "
                "user for input, never greet, never apologise. If the term is unfamiliar, say it is a "
                "cosmetic ingredient used in formulations. Output only the sentence. "
                + prompt_instruction(lang))},
            {"role": "user", "content": f"Define this cosmetic ingredient: {name}"},
        ], operation="explain_ingredient", max_output_tokens=_MAX_OUTPUT["explain_ingredient"])
        return IngredientExplanation(name=name, explanation=content.strip())

    def generate_outfit(self, occasion: str, items: list[dict], preferences: list[str],
                        lang: str = "en", avoid_item_ids: list[str] | None = None) -> dict:
        context = {
            "occasion": occasion,
            "preferences": preferences or [],
            "wardrobe": items,  # the RETRIEVED context — only the user's own pieces
            "alreadySuggested": avoid_item_ids or [],
        }
        content = self._chat(
            [
                {"role": "system", "content": _STYLIST_SYS + " " + prompt_instruction(lang)},
                {"role": "user", "content": json.dumps(context, ensure_ascii=False)},
            ],
            json_mode=True,
            operation="generate_outfit",
            max_output_tokens=_MAX_OUTPUT["generate_outfit"],
            # Taste, not fact: at 0 every ask returns the same look forever.
            temperature=0.8,
        )
        data = _extract_json(content)
        ids = data.get("itemIds") or data.get("item_ids") or []
        # The plan is the model's reasoning, kept out of the response but logged so a
        # bad pick can be traced to the formality it decided on.
        plan = data.get("plan") or {}
        log.debug("Stylist plan for %r: %s", occasion, plan)
        return {
            "formality": str(plan.get("formality") or "").strip().lower(),
            "season": str(plan.get("season") or "").strip().lower(),
            "itemIds": [str(x) for x in ids],
            # Empty rather than an English default: the backend fills a
            # localized title when this comes back blank.
            "title": str(data.get("title") or ""),
            "rationale": str(data.get("rationale") or ""),
        }

    def buy_advice(self, candidate: dict, matches: list[dict], scores: dict,
                   lang: str = "en") -> dict:
        context = {"candidate": candidate, "ownedSimilar": matches, "scores": scores}
        content = self._chat(
            [
                {"role": "system", "content": _BUY_SYS + " " + prompt_instruction(lang)},
                {"role": "user", "content": json.dumps(context, ensure_ascii=False)},
            ],
            json_mode=True,
            operation="buy_advice",
            max_output_tokens=_MAX_OUTPUT["buy_advice"],
        )
        data = _extract_json(content)
        verdict = str(data.get("verdict") or "maybe").lower()
        if verdict not in ("buy", "maybe", "skip"):
            verdict = "maybe"
        return {"verdict": verdict, "explanation": str(data.get("explanation") or "")}

    # ---- internals ----
    def _vision_json(self, system: str, prompt: str, image: bytes, filename: str,
                     operation: str = "unknown") -> dict:
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
            operation=operation,
            max_output_tokens=_MAX_OUTPUT.get(operation, _DEFAULT_MAX_OUTPUT),
        )
        return _extract_json(content)

    def _chat(self, messages: list, json_mode: bool = False, temperature: float = 0.0,
              operation: str = "unknown", max_output_tokens: int = _DEFAULT_MAX_OUTPUT) -> str:
        """Cataloguing calls keep temperature 0 so the same photo always catalogues
        the same way. Styling is a matter of taste, not fact: at 0 the same wardrobe
        and occasion return one identical outfit forever, so those callers raise it.

        Every call is capped and counted. A model that rambles is billed for the
        rambling, and until the tokens are recorded there is no answer to "what does
        one outfit cost", which is the number any paid allowance has to be built on.
        """
        payload: dict = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_output_tokens,
        }
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
            body = r.json()
        self._record_usage(operation, body)
        return body["choices"][0]["message"]["content"]

    def _record_usage(self, operation: str, body: dict) -> None:
        """
        Log what the provider says the call cost, in tokens.

        Logged rather than returned because the callers' contracts are the app's
        schemas, and a usage field has no place in a garment. The line is
        machine-readable on purpose: it is the raw material for the per-operation
        cost measurement, and no prompt, photo or user content goes into it.
        """
        usage = body.get("usage") or {}
        if not usage:
            return
        finish = ((body.get("choices") or [{}])[0] or {}).get("finish_reason")
        if finish == "length":
            # The answer was cut off by our own cap, so the result is probably
            # truncated JSON. Worth knowing before it looks like a model failure.
            log.warning("VLM %s hit the output cap on %s", self.model, operation)
        log.info(
            "vlm_usage operation=%s model=%s prompt_tokens=%s completion_tokens=%s total_tokens=%s",
            operation, self.model,
            usage.get("prompt_tokens"), usage.get("completion_tokens"), usage.get("total_tokens"),
        )


def _shrink(image: bytes, max_side: int = 512) -> bytes:
    """
    Downscale to a small JPEG to minimise vision-token cost.

    Raises when the bytes cannot be decoded rather than passing them through.
    The caller labels the result `data:image/jpeg`, so returning whatever arrived
    tells the API the wrong format about it: an undecodable photo came back as a
    400 "unsupported image" from the provider, which reads as a broken key or a
    spent quota rather than as a file we never managed to open.
    """
    try:
        img = Image.open(io.BytesIO(image)).convert("RGB")
    except Exception as e:  # noqa: BLE001 — any decode failure, not just one kind
        raise ValueError(
            f"could not decode the image ({e}); a HEIC photo needs pillow-heif installed"
        ) from e
    img.thumbnail((max_side, max_side))
    out = io.BytesIO()
    img.save(out, "JPEG", quality=85)
    return out.getvalue()


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
