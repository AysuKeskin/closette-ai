package ai.closette.usage.model;

/**
 * The AI actions an allowance is counted in.
 *
 * Deliberately the things a person does, not the calls a server makes. Someone
 * understands "20 photos left this month"; nobody can act on "60 credits", and a
 * shared balance would mean reading one ingredient label quietly costs an outfit.
 * Each operation is counted on its own.
 */
public enum AiOperation {

    /** Catalogue one garment or beauty product, from a photo or a description. */
    PHOTO_ANALYSIS,

    /** Compose one outfit. Every regeneration is another one; reopening a saved look is not. */
    OUTFIT,

    /** Assess one potential purchase, including reading the candidate. */
    SHOPPING_ADVICE,

    /** Read one ingredient label. */
    INGREDIENTS_OCR,

    /** Explain one ingredient we have not explained before. A cached answer costs nothing. */
    INGREDIENT_EXPLANATION;

    /** Config key, e.g. PHOTO_ANALYSIS → "photo-analysis". */
    public String key() {
        return name().toLowerCase().replace('_', '-');
    }
}
