package ai.closette.wishlist.dto;

import ai.closette.wishlist.model.WishlistItem;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request/response DTOs for the wishlist domain (FR-11). */
public final class WishlistDtos {

    private WishlistDtos() {
    }

    public record CreateWishlistItemRequest(
            @NotBlank(message = "Product name is required") String productName,
            String brand,
            BigDecimal price,
            String category,
            String imageKey
    ) {
    }

    public record WishlistItemResponse(
            UUID id,
            String productName,
            String brand,
            BigDecimal price,
            String category,
            String imageUrl,
            Instant createdAt
    ) {
        public static WishlistItemResponse from(WishlistItem i, String imageUrl) {
            return new WishlistItemResponse(i.getId(), i.getProductName(), i.getBrand(),
                    i.getPrice(), i.getCategory(), imageUrl, i.getCreatedAt());
        }
    }
}
