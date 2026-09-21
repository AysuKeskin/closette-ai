"""The languages the AI layer answers in.

Only free prose is translated — an explanation, a rationale, a verdict. The
structured attributes a garment is *catalogued* with (category, colours, styles,
seasons) stay canonical English whatever the user's language, because the
backend stores and matches on them; the app translates them for display.

``subcategory`` is the exception, and belongs with the prose: it is free text
("cropped cardigan"), nothing filters on it, and it reaches the user unchanged —
as the name their new item is prefilled with. A closed vocabulary can be
translated in the app; free text the model invents cannot, so it has to be
written in the user's language in the first place.
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


# ``subcategory`` is free text the user reads, so unlike the fixed vocabularies
# around it, the model has to write it in their language.
_SUBCATEGORY_INSTRUCTION = {
    "en": "Write subcategory in English.",
    "tr": (
        "Write subcategory in Turkish, as a Turkish shopper would name the garment "
        "(e.g. 'kısa hırka', 'bol kesim blazer', 'midi etek'). Every OTHER value — "
        "category, pattern, styles, seasons, colors — stays canonical English."
    ),
}


def subcategory_instruction(lang: str | None) -> str:
    """The clause that decides which language the free-text subcategory is written in."""
    return _SUBCATEGORY_INSTRUCTION[normalize(lang)]
