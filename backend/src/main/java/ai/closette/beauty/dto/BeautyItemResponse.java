package ai.closette.beauty.dto;

import ai.closette.beauty.model.BeautyCategory;
import ai.closette.beauty.model.BeautyItem;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BeautyItemResponse(
        UUID id,
        String brand,
        String productName,
        BeautyCategory category,
        String imageUrl,
        String size,
        List<String> ingredients,
        LocalDate purchaseDate,
        LocalDate openedDate,
        LocalDate expiryDate,
        Integer paoMonths,
        Integer amountRemaining,
        boolean favorite
) {

    public static BeautyItemResponse from(BeautyItem i, String imageUrl) {
        return new BeautyItemResponse(
                i.getId(), i.getBrand(), i.getProductName(), i.getCategory(), imageUrl, i.getSize(),
                i.getIngredients(), i.getPurchaseDate(), i.getOpenedDate(), i.getExpiryDate(),
                i.getPaoMonths(), i.getAmountRemaining(), i.isFavorite());
    }
}
