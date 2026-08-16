package ai.closette.wardrobe.service;

import ai.closette.auth.service.AuthService;
import ai.closette.auth.dto.RegisterRequest;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.model.WardrobeFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the wardrobe add + list + filter path end-to-end against the H2 DB
 * (the persistence half of Flow A; the AI/storage calls happen in the analyze step).
 */
@SpringBootTest
@ActiveProfiles("test")
class WardrobeServiceTest {

    @Autowired
    AuthService authService;

    @Autowired
    WardrobeService wardrobeService;

    @Test
    void createsAndFiltersItems() {
        UUID userId = authService.register(new RegisterRequest(
                "wardrobe-" + UUID.randomUUID() + "@test.io",
                "user_" + System.nanoTime(),
                "Password123",
                "Test")).user().id();

        CreateItemRequest req = new CreateItemRequest(
                "Black mini dress", ClothingCategory.DRESSES, "mini dress",
                List.of("black"), "solid", List.of("minimal", "elegant"),
                List.of("spring", "summer"), null, null, null, false);

        WardrobeItemResponse created = wardrobeService.create(userId, req);
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Black mini dress");
        assertThat(created.colors()).contains("black");

        // No filter → returns the item.
        assertThat(wardrobeService.list(userId, new WardrobeFilter(null, null, null, null, null, null)))
                .hasSize(1);

        // Category filter matches.
        assertThat(wardrobeService.list(userId,
                new WardrobeFilter(ClothingCategory.DRESSES, null, null, null, null, null)))
                .hasSize(1);

        // Non-matching category filter.
        assertThat(wardrobeService.list(userId,
                new WardrobeFilter(ClothingCategory.SHOES, null, null, null, null, null)))
                .isEmpty();

        // Color filter (in-memory).
        assertThat(wardrobeService.list(userId,
                new WardrobeFilter(null, "black", null, null, null, null)))
                .hasSize(1);
        assertThat(wardrobeService.list(userId,
                new WardrobeFilter(null, "red", null, null, null, null)))
                .isEmpty();
    }
}
