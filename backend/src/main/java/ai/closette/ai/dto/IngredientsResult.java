package ai.closette.ai.dto;

import java.util.List;

/** Ingredients read (OCR) from a photo of a product's ingredient list. Mirrors the AI service schema. */
public record IngredientsResult(
        List<String> ingredients
) {
}
