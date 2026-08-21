import hashlib

from app.providers.base import AIProvider
from app.schemas.analysis import BeautyAnalysis, ClothingAnalysis, IngredientExplanation

# Deterministic option pools — the mock picks from these based on a hash of the
# image bytes, so the same photo always yields the same (believable) result.
_CATEGORIES = [
    ("dress", "mini dress", ["minimal", "elegant"], ["spring", "summer"]),
    ("top", "silk blouse", ["classic", "feminine"], ["spring", "fall"]),
    ("bottom", "midi skirt", ["minimal", "chic"], ["fall", "winter"]),
    ("shoes", "ankle boots", ["edgy", "classic"], ["fall", "winter"]),
    ("bag", "shoulder bag", ["minimal"], ["spring", "summer", "fall", "winter"]),
    ("outerwear", "trench coat", ["classic", "timeless"], ["fall", "spring"]),
]
_COLORS = [["black"], ["white"], ["beige", "cream"], ["burgundy"], ["dusty pink"], ["navy"]]
_PATTERNS = ["solid", "striped", "floral", "checked"]

_INGREDIENTS = {
    "niacinamide": "A form of vitamin B3 commonly used in skincare to support the skin barrier and even out tone.",
    "hyaluronic acid": "A humectant that helps skin hold on to water, giving a plumper, more hydrated feel.",
    "retinol": "A vitamin A derivative used to support cell turnover; often introduced gradually.",
    "salicylic acid": "A beta-hydroxy acid (BHA) that exfoliates inside pores; common in products aimed at oily skin.",
    "glycerin": "A widely used humectant that draws moisture into the skin.",
    "ceramide": "A lipid naturally found in skin that helps maintain the moisture barrier.",
    "vitamin c": "An antioxidant used in skincare, often associated with brightening and evening skin tone.",
    "spf": "Indicates sun protection factor — how much UVB protection a sunscreen provides.",
}


def _index(image: bytes, modulo: int) -> int:
    digest = hashlib.sha256(image or b"seed").digest()
    return digest[0] % modulo


class MockProvider(AIProvider):
    """No-credentials provider that returns realistic, deterministic data so the
    whole app works end-to-end during development (Flow A)."""

    def analyze_clothing(self, image: bytes, filename: str) -> ClothingAnalysis:
        category, subcategory, styles, seasons = _CATEGORIES[_index(image, len(_CATEGORIES))]
        colors = _COLORS[_index(image + b"c", len(_COLORS))]
        pattern = _PATTERNS[_index(image + b"p", len(_PATTERNS))]
        return ClothingAnalysis(
            category=category,
            subcategory=subcategory,
            colors=colors,
            pattern=pattern,
            styles=styles,
            seasons=seasons,
            confidence=0.72,
        )

    def analyze_beauty(self, image: bytes, filename: str) -> BeautyAnalysis:
        brands = ["CeraVe", "The Ordinary", "La Roche-Posay", "Glossier"]
        products = ["Hydrating Cleanser", "Niacinamide 10%", "Moisturizing Cream", "Lip Balm"]
        cats = ["skincare", "skincare", "skincare", "makeup"]
        i = _index(image, len(brands))
        return BeautyAnalysis(
            brand=brands[i],
            product_name=products[i],
            category=cats[i],
            confidence=0.61,
        )

    def explain_ingredient(self, name: str) -> IngredientExplanation:
        key = name.strip().lower()
        explanation = _INGREDIENTS.get(
            key,
            f"{name} is a cosmetic ingredient. We don't have a verified description for it yet — "
            "check the product packaging or a trusted source for details.",
        )
        return IngredientExplanation(name=name, explanation=explanation)

    def generate_outfit(self, occasion: str, items: list[dict], preferences: list[str]) -> dict:
        # Deterministic rule-based composer (no LLM): a dress, or top + bottom,
        # then complete with one of each supporting category.
        by_cat: dict[str, list[dict]] = {}
        for it in items:
            by_cat.setdefault(str(it.get("category", "")).lower(), []).append(it)

        chosen: list[dict] = []
        if by_cat.get("dress") or by_cat.get("dresses"):
            chosen.append((by_cat.get("dress") or by_cat.get("dresses"))[0])
        else:
            if by_cat.get("top") or by_cat.get("tops"):
                chosen.append((by_cat.get("top") or by_cat.get("tops"))[0])
            if by_cat.get("bottom") or by_cat.get("bottoms"):
                chosen.append((by_cat.get("bottom") or by_cat.get("bottoms"))[0])
        for cat in ("outerwear", "shoes", "bag", "bags", "jewelry", "accessory", "accessories"):
            if by_cat.get(cat):
                chosen.append(by_cat[cat][0])

        occ = (occasion or "").strip() or "your day"
        return {
            "itemIds": [it["id"] for it in chosen if it.get("id")],
            "title": "Your look",
            "rationale": f"A complete look for {occ} built from {len(chosen)} pieces you already own.",
        }

    def buy_advice(self, candidate: dict, matches: list[dict], scores: dict) -> dict:
        # Rule-based verdict (no LLM): own several similar → skip; good fit → buy.
        similar = int(scores.get("similarItemCount", 0))
        match_score = int(scores.get("matchScore", 0))
        if similar >= 2:
            verdict = "skip"
            why = f"You already own {similar} similar pieces, so this would mostly duplicate your wardrobe."
        elif match_score >= 40:
            verdict = "buy"
            why = "It fits your style and works with several pieces you already own."
        else:
            verdict = "maybe"
            why = "It could work, but it doesn't strongly connect to what you already wear."
        return {"verdict": verdict, "explanation": why}
