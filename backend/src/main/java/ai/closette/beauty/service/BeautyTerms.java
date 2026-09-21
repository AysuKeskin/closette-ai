package ai.closette.beauty.service;

import ai.closette.beauty.model.BeautyCategory;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The beauty vocabulary, in both languages.
 *
 * The product sources are mixed: Open Beauty Facts carries Turkish products with
 * Turkish names, while the Makeup API catalogue is entirely English. A shopper
 * searching "ruj" should reach both, and the lipstick they find should be filed
 * under MAKEUP whichever language named it.
 */
final class BeautyTerms {

    private BeautyTerms() {
    }

    /**
     * Lowercased and stripped down to ASCII letters for matching.
     *
     * Neither locale can lowercase this text alone: Turkish turns "LIPSTICK" into
     * "lıpstıck", and the root locale turns "İ" into an i plus a combining dot.
     * Folding the Turkish letters to their ASCII shapes sidesteps both, and lets one
     * keyword list match "güneş" and "gunes" alike — real catalogue data has both.
     * It also flattens the non-breaking spaces that OBF product names carry.
     */
    static String fold(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case 'ı' -> out.append('i');
                case 'ş' -> out.append('s');
                case 'ğ' -> out.append('g');
                case 'ü' -> out.append('u');
                case 'ö' -> out.append('o');
                case 'ç' -> out.append('c');
                case 'â' -> out.append('a');
                case 'î' -> out.append('i');
                case 'û' -> out.append('u');
                // The combining dot Locale.ROOT leaves behind when it lowercases "İ".
                case '\u0307' -> { }
                // isWhitespace alone is not enough: it reports false for the
                // non-breaking space that OBF product names are littered with.
                default -> out.append(Character.isWhitespace(c) || Character.isSpaceChar(c) ? ' ' : c);
            }
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * Turkish product words → the English the catalogues are written in.
     *
     * Only the words a shopper actually types into a search box; this is not a
     * dictionary, and an unmapped word simply searches as typed.
     */
    private static final Map<String, String> TR_TO_EN = Map.ofEntries(
            Map.entry("ruj", "lipstick"),
            Map.entry("maskara", "mascara"),
            Map.entry("rimel", "mascara"),
            Map.entry("fondoten", "foundation"),
            Map.entry("allik", "blush"),
            Map.entry("kapatici", "concealer"),
            Map.entry("pudra", "powder"),
            Map.entry("far", "eyeshadow"),
            Map.entry("eyeliner", "eyeliner"),
            Map.entry("oje", "nail polish"),
            Map.entry("tirnak", "nail"),
            Map.entry("parfum", "perfume"),
            Map.entry("sampuan", "shampoo"),
            Map.entry("sac", "hair"),
            Map.entry("krem", "cream"),
            Map.entry("nemlendirici", "moisturizer"),
            Map.entry("temizleyici", "cleanser"),
            Map.entry("tonik", "toner"),
            Map.entry("serum", "serum"),
            Map.entry("maske", "mask"),
            Map.entry("gunes", "sunscreen"),
            Map.entry("dudak", "lip"),
            Map.entry("goz", "eye"),
            Map.entry("kas", "brow"),
            Map.entry("vucut", "body"),
            Map.entry("losyon", "lotion"),
            Map.entry("deodorant", "deodorant"),
            Map.entry("dus", "shower"),
            Map.entry("sabun", "soap"),
            Map.entry("bakim", "care"),
            Map.entry("peeling", "scrub"));

    /**
     * The query as typed, plus its English reading when the two differ.
     *
     * Returned rather than substituted: "ruj" has to keep reaching the Turkish
     * products that are only ever named "ruj".
     */
    static Set<String> expand(String query) {
        Set<String> out = new LinkedHashSet<>();
        String folded = fold(query);
        if (folded.isBlank()) return out;
        out.add(folded);

        StringBuilder translated = new StringBuilder();
        boolean changed = false;
        for (String word : folded.split(" ")) {
            String english = TR_TO_EN.get(word);
            if (english != null && !english.equals(word)) changed = true;
            translated.append(english == null ? word : english).append(' ');
        }
        if (changed) out.add(translated.toString().trim());
        return out;
    }

    private static final Map<BeautyCategory, String[]> CATEGORY_KEYWORDS = Map.of(
            BeautyCategory.MAKEUP, new String[]{
                    "lipstick", "mascara", "foundation", "makeup", "make-up", "eyeliner", "blush",
                    "concealer", "eyeshadow", "highlighter", "bronzer", "lip gloss", "lipgloss",
                    "ruj", "maskara", "rimel", "fondoten", "allik", "kapatici", "far",
                    "aydinlatici", "makyaj", "dudak parlatici", "lip tint"},
            BeautyCategory.HAIRCARE, new String[]{
                    "shampoo", "conditioner", "hair", "sampuan", "sac", "krem sac", "durulanmayan"},
            BeautyCategory.PERFUME, new String[]{
                    "perfume", "fragrance", "eau de", "cologne", "parfum", "kolonya"},
            BeautyCategory.NAILS, new String[]{
                    "nail", "polish", "tirnak", "oje"},
            BeautyCategory.BODYCARE, new String[]{
                    "body", "lotion", "shower", "deodorant", "vucut", "losyon", "dus jeli",
                    "sabun", "el kremi", "hand cream"});

    /**
     * Best guess at what a product is, from whatever text the source gave us.
     *
     * Order is deliberate. The narrow vocabularies are asked first, because their
     * words are unambiguous ("oje" is nail polish, never makeup); makeup then wins
     * over bodycare, because "nemlendirici dudak parlatıcısı" is a lip gloss and not
     * a moisturiser. Skincare is the fallback rather than a keyword list — most of
     * what is left over genuinely is skincare.
     */
    static BeautyCategory guessCategory(String haystack) {
        String h = fold(haystack);
        for (BeautyCategory category : new BeautyCategory[]{
                BeautyCategory.NAILS, BeautyCategory.PERFUME, BeautyCategory.HAIRCARE,
                BeautyCategory.MAKEUP, BeautyCategory.BODYCARE}) {
            for (String keyword : CATEGORY_KEYWORDS.get(category)) {
                if (h.contains(keyword)) return category;
            }
        }
        return BeautyCategory.SKINCARE;
    }
}
