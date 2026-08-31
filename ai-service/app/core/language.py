"""The languages the AI layer answers in.

Only free prose is translated — an explanation, a rationale, a verdict. The
structured attributes a garment is *catalogued* with (category, colours, styles,
seasons) stay canonical English whatever the user's language, because the
backend stores and matches on them; the app translates them for display.
"""
from __future__ import annotations

SUPPORTED = ("en", "tr")
DEFAULT = "en"

# Appended to every VLM system prompt that produces prose the user reads.
_PROMPT_INSTRUCTION = {
    "en": "Write any prose (title, rationale, explanation) in English.",
    "tr": (
        "Write any prose (title, rationale, explanation) in Turkish, in a warm, "
        "natural everyday register — not a literal translation. Keep JSON keys "
        "and any category/colour/style values in English."
    ),
}


def normalize(lang: str | None) -> str:
    """Accepts 'tr', 'TR', 'tr-TR'; anything unknown falls back to English."""
    if not lang:
        return DEFAULT
    code = lang.strip().lower().replace("_", "-").split("-")[0]
    return code if code in SUPPORTED else DEFAULT


def prompt_instruction(lang: str | None) -> str:
    return _PROMPT_INSTRUCTION[normalize(lang)]
