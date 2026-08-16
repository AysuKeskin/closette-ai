package ai.closette.wardrobe.dto;

import ai.closette.wardrobe.model.ClothingCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Payload to save a wardrobe item. In Flow A this is the (possibly edited) AI
 * analysis result plus the {@code imageKey} returned by the analyze step.
 */
public record CreateItemRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Category is required")
        ClothingCategory category,

        String subcategory,
        List<String> colors,
        String pattern,
        List<String> styles,
        List<String> seasons,
        String brand,
        String size,
        String imageKey,
        Boolean favorite
) {
}
