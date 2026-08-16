package ai.closette.wardrobe.dto;

import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.model.WardrobeItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WardrobeItemResponse(
        UUID id,
        String name,
        ClothingCategory category,
        String subcategory,
        List<String> colors,
        String pattern,
        List<String> styles,
        List<String> seasons,
        String brand,
        String size,
        String imageUrl,
        boolean favorite,
        Instant createdAt
) {

    /** Maps an entity to its response, resolving the image key to a signed URL. */
    public static WardrobeItemResponse from(WardrobeItem item, String imageUrl) {
        return new WardrobeItemResponse(
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getSubcategory(),
                item.getColors(),
                item.getPattern(),
                item.getStyles(),
                item.getSeasons(),
                item.getBrand(),
                item.getSize(),
                imageUrl,
                item.isFavorite(),
                item.getCreatedAt());
    }
}
