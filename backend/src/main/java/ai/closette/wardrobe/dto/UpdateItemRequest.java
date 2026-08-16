package ai.closette.wardrobe.dto;

import ai.closette.wardrobe.model.ClothingCategory;

import java.util.List;

/** Partial update — only non-null fields are applied. */
public record UpdateItemRequest(
        String name,
        ClothingCategory category,
        String subcategory,
        List<String> colors,
        String pattern,
        List<String> styles,
        List<String> seasons,
        String brand,
        String size,
        Boolean favorite
) {
}
