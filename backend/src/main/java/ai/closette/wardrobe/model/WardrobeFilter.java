package ai.closette.wardrobe.model;

/**
 * Optional filters for wardrobe search (FR-03). Any null field is ignored.
 */
public record WardrobeFilter(
        ClothingCategory category,
        String color,
        String season,
        String brand,
        Boolean favorite,
        String q
) {
}
