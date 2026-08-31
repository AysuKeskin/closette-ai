package ai.closette.beauty.dto;

import ai.closette.beauty.model.BeautyCategory;

import java.util.List;

/**
 * A product looked up from Open Beauty Facts (barcode or name search) — the
 * editable candidate the user confirms before saving to their shelf (Flow B).
 */
public record BeautyProductCandidate(
        String barcode,
        String productName,
        String brand,
        BeautyCategory category,
        List<String> ingredients,
        String imageUrl
) {
}
