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
        @NotBlank(message = "{validation.name.required}")
        String name,

        @NotNull(message = "{validation.category.required}")
        ClothingCategory category,

        String subcategory,
        List<String> colors,
        /**
         * Measured shares as "name:percent", straight from the analysis. Optional:
         * an item typed in by hand has colours but no measurement behind them.
         */
        List<String> colorShares,
        String pattern,
        List<String> styles,
        List<String> seasons,
        String brand,
        String size,
        String imageKey,
        Boolean favorite
) {
}
