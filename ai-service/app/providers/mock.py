import hashlib

from app.core.language import normalize as normalize_lang
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

# Keyword → category for parsing free-text descriptions. Ordered longest-first at match time.
# Turkish words map to the same canonical English values: a description typed in
# Turkish must produce the same catalogue attributes as its English equivalent,
# because that is what gets stored and matched.
_TEXT_CATEGORY = {
    "dress": "dress", "gown": "dress", "jumpsuit": "dress",
    "blazer": "outerwear", "coat": "outerwear", "trench": "outerwear", "jacket": "outerwear",
    "cardigan": "outerwear", "parka": "outerwear",
    "blouse": "top", "shirt": "top", "tee": "top", "t-shirt": "top", "sweater": "top",
    "knit": "top", "tank": "top", "hoodie": "top", "top": "top", "cami": "top",
    "jeans": "bottom", "trousers": "bottom", "pants": "bottom", "skirt": "bottom",
    "shorts": "bottom", "leggings": "bottom", "bottom": "bottom",
    "boots": "shoes", "sneakers": "shoes", "heels": "shoes", "sandals": "shoes",
    "loafers": "shoes", "flats": "shoes", "shoes": "shoes",
    "bag": "bag", "purse": "bag", "tote": "bag", "clutch": "bag", "backpack": "bag",
    "scarf": "accessory", "belt": "accessory", "hat": "accessory", "sunglasses": "accessory",
    "necklace": "accessory", "earrings": "accessory",
}

_TEXT_CATEGORY_TR = {
    "elbise": "dress", "tulum": "dress",
    "ceket": "outerwear", "mont": "outerwear", "kaban": "outerwear", "trençkot": "outerwear",
    "hırka": "outerwear", "blazer ceket": "outerwear",
    "gömlek": "top", "bluz": "top", "tişört": "top", "kazak": "top", "süveter": "top",
    "atlet": "top", "üst": "top", "body": "top",
    "pantolon": "bottom", "etek": "bottom", "kot": "bottom", "şort": "bottom",
    "tayt": "bottom", "alt": "bottom",
    "bot": "shoes", "çizme": "shoes", "topuklu": "shoes", "sandalet": "shoes",
    "spor ayakkabı": "shoes", "ayakkabı": "shoes", "babet": "shoes",
    "çanta": "bag", "sırt çantası": "bag", "el çantası": "bag", "cüzdan": "bag",
    "atkı": "accessory", "şal": "accessory", "kemer": "accessory", "şapka": "accessory",
    "gözlük": "accessory", "kolye": "accessory", "küpe": "accessory", "bileklik": "accessory",
}

# Every category keyword the parser understands, in either language.
_TEXT_CATEGORY_ALL = {**_TEXT_CATEGORY, **_TEXT_CATEGORY_TR}

# Turkish colour/style words → the canonical English value stored on the item.
_TEXT_ALIASES = {
    "siyah": "black", "beyaz": "white", "krem": "cream", "bej": "beige", "gri": "grey",
    "lacivert": "navy", "mavi": "blue", "pembe": "pink", "pudra": "blush", "kırmızı": "red",
    "bordo": "burgundy", "yeşil": "green", "haki": "khaki", "kahverengi": "brown",
    "camel": "camel", "mor": "purple", "lila": "lilac", "sarı": "yellow", "hardal": "mustard",
    "turuncu": "orange", "altın": "gold", "gümüş": "silver", "mercan": "coral",
    "gül kurusu": "dusty pink", "bebe mavisi": "sky blue",
    "sade": "minimal", "minimal": "minimal", "klasik": "classic", "şık": "chic",
    "zarif": "elegant", "günlük": "casual", "rahat": "casual", "spor": "sporty",
    "abiye": "formal", "resmi": "formal", "romantik": "romantic", "feminen": "feminine",
    "bohem": "boho", "vintage": "vintage", "oversize": "oversized", "dar": "fitted",
}
_TEXT_COLORS = [
    "dusty pink", "sky blue", "black", "white", "cream", "ivory", "beige", "grey", "gray",
    "navy", "blue", "denim", "pink", "blush", "red", "burgundy", "maroon", "green", "sage",
    "olive", "emerald", "brown", "camel", "tan", "khaki", "lavender", "purple", "lilac",
    "yellow", "mustard", "orange", "gold", "silver", "coral", "teal",
]
_TEXT_STYLES = [
    "minimal", "minimalist", "classic", "elegant", "casual", "chic", "edgy", "boho", "bohemian",
    "romantic", "sporty", "streetwear", "preppy", "feminine", "timeless", "trendy", "oversized",
    "fitted", "vintage", "cottagecore", "formal",
]

_INGREDIENTS = {
    "en": {
        "niacinamide": "A form of vitamin B3 commonly used in skincare to support the skin barrier and even out tone.",
        "hyaluronic acid": "A humectant that helps skin hold on to water, giving a plumper, more hydrated feel.",
        "retinol": "A vitamin A derivative used to support cell turnover; often introduced gradually.",
        "salicylic acid": "A beta-hydroxy acid (BHA) that exfoliates inside pores; common in products aimed at oily skin.",
        "glycerin": "A widely used humectant that draws moisture into the skin.",
        "ceramide": "A lipid naturally found in skin that helps maintain the moisture barrier.",
        "vitamin c": "An antioxidant used in skincare, often associated with brightening and evening skin tone.",
        "spf": "Indicates sun protection factor — how much UVB protection a sunscreen provides.",
    },
    "tr": {
        "niacinamide": "B3 vitamininin bir formu; cilt bakımında cilt bariyerini desteklemek ve ton eşitsizliğini azaltmak için sık kullanılır.",
        "hyaluronic acid": "Cildin suyu tutmasına yardımcı olan bir nemlendirici (humektant); daha dolgun ve nemli bir his verir.",
        "retinol": "A vitamini türevi; hücre yenilenmesini desteklemek için kullanılır, genelde azar azar alıştırılarak başlanır.",
        "salicylic acid": "Gözenek içinde arındıran bir beta hidroksi asit (BHA); yağlı cilde yönelik ürünlerde yaygındır.",
        "glycerin": "Cilde nem çeken, çok yaygın kullanılan bir humektant.",
        "ceramide": "Ciltte doğal olarak bulunan, nem bariyerini korumaya yardımcı bir lipit.",
        "vitamin c": "Cilt bakımında kullanılan bir antioksidan; genelde aydınlatma ve ton eşitleme ile anılır.",
        "spf": "Güneş koruma faktörünü belirtir — bir güneş kreminin ne kadar UVB koruması sağladığını gösterir.",
    },
}

# Prose the mock hands back. The real VLM writes its own; these keep the offline
# app fully bilingual, and keep the tests deterministic in both languages.
_PROSE = {
    "en": {
        "ingredient_unknown": ("{name} is a cosmetic ingredient. We don't have a verified description for it "
                               "yet, so check the product packaging or a trusted source for details."),
        "outfit_title": "Your look",
        "outfit_occasion_fallback": "your day",
        "outfit_rationale": "A complete look for {occasion} built from {count} pieces you already own.",
        "buy_skip": "You already own {similar} similar pieces, so this would mostly duplicate your wardrobe.",
        "buy_buy": "It fits your style and works with several pieces you already own.",
        "buy_maybe": "It could work, but it doesn't strongly connect to what you already wear.",
    },
    "tr": {
        "ingredient_unknown": ("{name} bir kozmetik içeriği. Bunun için henüz doğrulanmış bir açıklamamız yok; "
                               "ürünün ambalajına ya da güvenilir bir kaynağa bakabilirsin."),
        "outfit_title": "Kombinin",
        "outfit_occasion_fallback": "bugün",
        "outfit_rationale": "{occasion} için gardırobundaki {count} parçadan kurulmuş eksiksiz bir kombin.",
        "buy_skip": "Zaten benzer {similar} parçan var, bu büyük ölçüde gardırobunu tekrarlar.",
        "buy_buy": "Tarzına uyuyor ve gardırobundaki birkaç parçayla birlikte kullanabilirsin.",
        "buy_maybe": "İşine yarayabilir ama şu an giydiklerinle güçlü bir bağı yok.",
    },
}


def _say(lang: str, key: str, **params) -> str:
    return _PROSE[normalize_lang(lang)][key].format(**params)


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

    def extract_ingredients(self, image: bytes, filename: str) -> list[str]:
        # Deterministic believable OCR result so the flow works end-to-end without a model.
        pool = ["aqua", "glycerin", "niacinamide", "hyaluronic acid", "ceramide", "panthenol", "tocopherol"]
        start = _index(image, len(pool))
        return [pool[(start + i) % len(pool)] for i in range(4)]

    def parse_clothing(self, description: str) -> ClothingAnalysis:
        text = (description or "").lower()
        category, subcategory = None, "item"
        # Longest keywords first so "dusty pink"/"t-shirt" win over their substrings.
        for kw in sorted(_TEXT_CATEGORY_ALL, key=len, reverse=True):
            if kw in text:
                category = _TEXT_CATEGORY_ALL[kw]
                # A Turkish keyword still yields an English subcategory: this value
                # is stored on the item and must not depend on the input language.
                subcategory = kw if kw in _TEXT_CATEGORY else category
                break
        colors = [c for c in _TEXT_COLORS if c in text]
        # Normalise "minimalist"→"minimal", "bohemian"→"boho" for consistency with the wardrobe tags.
        styles = []
        for s in _TEXT_STYLES:
            if s in text:
                styles.append({"minimalist": "minimal", "bohemian": "boho"}.get(s, s))
        # Turkish words map onto the same canonical values.
        for word, canonical in _TEXT_ALIASES.items():
            if word in text:
                (colors if canonical in _TEXT_COLORS else styles).append(canonical)
        colors = list(dict.fromkeys(colors))
        # Nothing recognised → say so ("unknown"), don't guess a category.
        if category is None and not colors and not styles:
            return ClothingAnalysis(category="unknown", subcategory="", colors=[], pattern="", styles=[], confidence=0.0)
        return ClothingAnalysis(
            category=category or "unknown",
            subcategory=subcategory,
            colors=colors,
            pattern="solid",
            styles=list(dict.fromkeys(styles)),
            confidence=0.5,
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

    def explain_ingredient(self, name: str, lang: str = "en") -> IngredientExplanation:
        key = name.strip().lower()
        explanation = _INGREDIENTS[normalize_lang(lang)].get(
            key, _say(lang, "ingredient_unknown", name=name))
        return IngredientExplanation(name=name, explanation=explanation)

    def generate_outfit(self, occasion: str, items: list[dict], preferences: list[str],
                        lang: str = "en") -> dict:
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

        occ = (occasion or "").strip() or _say(lang, "outfit_occasion_fallback")
        return {
            "itemIds": [it["id"] for it in chosen if it.get("id")],
            "title": _say(lang, "outfit_title"),
            "rationale": _say(lang, "outfit_rationale", occasion=occ, count=len(chosen)),
        }

    def buy_advice(self, candidate: dict, matches: list[dict], scores: dict,
                   lang: str = "en") -> dict:
        # Rule-based verdict (no LLM): own several similar → skip; good fit → buy.
        similar = int(scores.get("similarItemCount", 0))
        match_score = int(scores.get("matchScore", 0))
        if similar >= 2:
            verdict = "skip"
            why = _say(lang, "buy_skip", similar=similar)
        elif match_score >= 40:
            verdict = "buy"
            why = _say(lang, "buy_buy")
        else:
            verdict = "maybe"
            why = _say(lang, "buy_maybe")
        return {"verdict": verdict, "explanation": why}
