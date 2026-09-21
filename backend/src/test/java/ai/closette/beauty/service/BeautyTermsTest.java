package ai.closette.beauty.service;

import ai.closette.beauty.model.BeautyCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BeautyTermsTest {

    @Test
    void foldsTurkishLettersSoOneKeywordListMatchesBothLanguages() {
        assertThat(BeautyTerms.fold("Güneş Koruyucu")).isEqualTo("gunes koruyucu");
        assertThat(BeautyTerms.fold("İNCE")).isEqualTo("ince");
        assertThat(BeautyTerms.fold("Işıltı")).isEqualTo("isilti");
        // Neither locale alone survives a mixed catalogue: Turkish would give "lıpstıck".
        assertThat(BeautyTerms.fold("LIPSTICK")).isEqualTo("lipstick");
        // OBF product names carry non-breaking spaces, which broke token matching.
        assertThat(BeautyTerms.fold("Matte Lipstick Crayon")).isEqualTo("matte lipstick crayon");
    }

    @Test
    void readsATurkishQueryInEnglishWithoutLosingTheOriginal() {
        assertThat(BeautyTerms.expand("ruj")).containsExactly("ruj", "lipstick");
        assertThat(BeautyTerms.expand("nemlendirici krem")).containsExactly(
                "nemlendirici krem", "moisturizer cream");
        // Nothing to translate: one variant, so no source is queried twice.
        assertThat(BeautyTerms.expand("maybelline mascara")).containsExactly("maybelline mascara");
    }

    @Test
    void categorisesTurkishProductNames() {
        assertThat(BeautyTerms.guessCategory("Maybelline Super Stay Vinyl Ink Likit Parlak Ruj 120"))
                .isEqualTo(BeautyCategory.MAKEUP);
        assertThat(BeautyTerms.guessCategory("Flormar Nemlendirici Dudak Parlatıcısı"))
                .isEqualTo(BeautyCategory.MAKEUP);
        assertThat(BeautyTerms.guessCategory("Elidor Onarıcı Şampuan")).isEqualTo(BeautyCategory.HAIRCARE);
        assertThat(BeautyTerms.guessCategory("Flormar Oje 402")).isEqualTo(BeautyCategory.NAILS);
        assertThat(BeautyTerms.guessCategory("Bvlgari Parfüm")).isEqualTo(BeautyCategory.PERFUME);
        assertThat(BeautyTerms.guessCategory("Nivea Vücut Losyonu")).isEqualTo(BeautyCategory.BODYCARE);
        // Nothing recognised stays skincare, which is what most of the rest is.
        assertThat(BeautyTerms.guessCategory("Blistex Lip Relief Cream SPF 15"))
                .isEqualTo(BeautyCategory.SKINCARE);
    }

    @Test
    void englishNamesStillCategoriseAsBefore() {
        assertThat(BeautyTerms.guessCategory("Maybelline Great Lash Mascara")).isEqualTo(BeautyCategory.MAKEUP);
        assertThat(BeautyTerms.guessCategory("Head & Shoulders Shampoo")).isEqualTo(BeautyCategory.HAIRCARE);
        assertThat(BeautyTerms.guessCategory("Dior Eau de Parfum")).isEqualTo(BeautyCategory.PERFUME);
        assertThat(BeautyTerms.guessCategory("CeraVe Moisturising Lotion")).isEqualTo(BeautyCategory.BODYCARE);
        assertThat(BeautyTerms.guessCategory("The Ordinary Niacinamide 10%")).isEqualTo(BeautyCategory.SKINCARE);
    }

    @Test
    void shadeMarkersAreRecognisedButSpfIsNot() {
        // The dedupe key drops everything from a trailing shade marker, so the same
        // lipstick in thirty colours takes one row instead of thirty.
        assertThat(isShade("120")).isTrue();
        assertThat(isShade("no:27")).isTrue();
        assertThat(isShade("fc62")).isTrue();
        assertThat(isShade("6")).isTrue();
        assertThat(isShade("50ml")).isTrue();
        // SPF is spelled like a shade code but separates two genuinely different products.
        assertThat(isShade("spf50")).isFalse();
        assertThat(isShade("50spf")).isFalse();
        assertThat(isShade("punchy")).isFalse();
        assertThat(isShade("ruj")).isFalse();
    }

    /** Mirrors the guard in BeautyLookupService#dedupeKey. */
    private static boolean isShade(String word) {
        return !word.contains("spf") && word.matches("(no:?)?[a-z]{0,3}\\d+([.,]\\d+)?(ml|gr?|oz)?");
    }
}
