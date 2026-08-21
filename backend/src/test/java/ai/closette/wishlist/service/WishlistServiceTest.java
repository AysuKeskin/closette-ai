package ai.closette.wishlist.service;

import ai.closette.auth.service.AuthService;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.support.TestData;
import ai.closette.wishlist.dto.WishlistDtos.CreateWishlistItemRequest;
import ai.closette.wishlist.dto.WishlistDtos.WishlistItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Wishlist add/list/remove (FR-11). */
@SpringBootTest
@ActiveProfiles("test")
class WishlistServiceTest {

    @Autowired
    AuthService authService;

    @Autowired
    WishlistService wishlistService;

    private UUID newUser() {
        return TestData.newUser(authService);
    }

    private static CreateWishlistItemRequest request(String productName) {
        return new CreateWishlistItemRequest(productName, "COS", new BigDecimal("129.00"), "coat", null);
    }

    @Test
    void savedItemsKeepTheirDetails() {
        UUID userId = newUser();

        WishlistItemResponse created = wishlistService.create(userId, request("Wool coat"));

        assertThat(created.id()).isNotNull();
        assertThat(created.productName()).isEqualTo("Wool coat");
        assertThat(created.brand()).isEqualTo("COS");
        assertThat(created.price()).isEqualByComparingTo("129.00");
        assertThat(created.category()).isEqualTo("coat");
    }

    @Test
    void theProductNameIsTrimmed() {
        UUID userId = newUser();

        assertThat(wishlistService.create(userId, request("  Wool coat  ")).productName())
                .isEqualTo("Wool coat");
    }

    @Test
    void newestItemsAreListedFirst() {
        UUID userId = newUser();
        wishlistService.create(userId, request("Older"));
        wishlistService.create(userId, request("Newer"));

        assertThat(wishlistService.list(userId))
                .extracting(WishlistItemResponse::productName).containsExactly("Newer", "Older");
    }

    @Test
    void deleteRemovesTheItem() {
        UUID userId = newUser();
        WishlistItemResponse created = wishlistService.create(userId, request("Wool coat"));

        wishlistService.delete(userId, created.id());

        assertThat(wishlistService.list(userId)).isEmpty();
    }

    @Test
    void anotherUsersWishlistIsInvisibleAndUntouchable() {
        UUID owner = newUser();
        UUID stranger = newUser();
        WishlistItemResponse created = wishlistService.create(owner, request("Wool coat"));

        assertThat(wishlistService.list(stranger)).isEmpty();
        assertThatThrownBy(() -> wishlistService.delete(stranger, created.id()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(wishlistService.list(owner)).hasSize(1);
    }
}
