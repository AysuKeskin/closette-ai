package ai.closette.beauty.dto;

import ai.closette.beauty.model.BeautyCategory;

import java.util.List;

/** Partial update for a saved beauty product; only non-null fields are applied. */
public record UpdateBeautyItemRequest(
        String brand,
        String productName,
        BeautyCategory category,
        String size,
        List<String> ingredients,
        Boolean favorite,
        /** A key from a fresh /analyze upload. Null leaves the current photo alone. */
        String imageKey
) {
}
