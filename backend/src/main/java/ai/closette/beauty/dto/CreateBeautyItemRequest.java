package ai.closette.beauty.dto;

import ai.closette.beauty.model.BeautyCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateBeautyItemRequest(
        String brand,

        @NotBlank(message = "{validation.productName.required}")
        String productName,

        @NotNull(message = "{validation.category.required}")
        BeautyCategory category,

        String imageKey,
        String imageUrl,
        String size,
        List<String> ingredients,
        LocalDate purchaseDate,
        LocalDate openedDate,
        LocalDate expiryDate,
        Integer paoMonths,
        Integer amountRemaining,
        Boolean favorite
) {
}
