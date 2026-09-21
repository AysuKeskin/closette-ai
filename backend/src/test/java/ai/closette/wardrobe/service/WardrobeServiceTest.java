package ai.closette.wardrobe.service;

import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.ai.service.AIService;
import ai.closette.auth.service.AuthService;
import ai.closette.auth.dto.RegisterRequest;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.support.TestData;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.dto.RetagSummary;
import ai.closette.wardrobe.dto.UpdateItemRequest;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.model.WardrobeFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Exercises the wardrobe add + list + filter path end-to-end against the H2 DB
 * (the persistence half of Flow A; the AI/storage calls happen in the analyze step).
 *
 * The AI seam is mocked: re-cataloguing calls it once per item, and without this the
 * suite would reach whatever provider the developer happens to have configured.
 */
@SpringBootTest
@ActiveProfiles("test")
class WardrobeServiceTest {

    private static final WardrobeFilter NO_FILTER = new WardrobeFilter(null, null, null, null, null, null);

    @Autowired
    AuthService authService;

    @Autowired
    WardrobeService wardrobeService;

    @MockitoBean
    AIService aiService;

    private UUID newUser() {
        return TestData.newUser(authService);
    }

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

    @Test
    void blankOptionalFieldsAreStoredAsNull() {
        UUID userId = newUser();

        WardrobeItemResponse created = wardrobeService.create(userId, new CreateItemRequest(
                "  Silk blouse  ", ClothingCategory.TOPS, "   ", List.of("cream"), "  ",
                List.of(), List.of(), "   ", "  ", "   ", null));

        assertThat(created.name()).isEqualTo("Silk blouse");
        assertThat(created.subcategory()).isNull();
        assertThat(created.pattern()).isNull();
        assertThat(created.brand()).isNull();
        assertThat(created.size()).isNull();
        assertThat(created.favorite()).isFalse();
    }

    @Test
    void nullListsBecomeEmptyLists() {
        UUID userId = newUser();

        WardrobeItemResponse created = wardrobeService.create(userId, new CreateItemRequest(
                "Trench coat", ClothingCategory.OUTERWEAR, null, null, null, null, null,
                null, null, null, null));

        assertThat(created.colors()).isEmpty();
        assertThat(created.styles()).isEmpty();
        assertThat(created.seasons()).isEmpty();
    }

    @Test
    void colorAndSeasonFiltersIgnoreCase() {
        UUID userId = newUser();
        wardrobeService.create(userId, new CreateItemRequest(
                "Navy blazer", ClothingCategory.OUTERWEAR, null, List.of("navy"), "solid",
                List.of("classic"), List.of("fall"), null, null, null, null));

        assertThat(wardrobeService.list(userId, new WardrobeFilter(null, "NAVY", null, null, null, null))).hasSize(1);
        assertThat(wardrobeService.list(userId, new WardrobeFilter(null, null, "FALL", null, null, null))).hasSize(1);
        assertThat(wardrobeService.list(userId, new WardrobeFilter(null, null, "winter", null, null, null))).isEmpty();
    }

    @Test
    void brandAndSearchFiltersIgnoreCase() {
        UUID userId = newUser();
        wardrobeService.create(userId, new CreateItemRequest(
                "Wool Coat", ClothingCategory.OUTERWEAR, null, List.of("camel"), "solid",
                List.of("classic"), List.of("winter"), "Arket", null, null, null));

        assertThat(wardrobeService.list(userId, new WardrobeFilter(null, null, null, "arket", null, null))).hasSize(1);
        assertThat(wardrobeService.list(userId, new WardrobeFilter(null, null, null, null, null, "WOOL"))).hasSize(1);
        assertThat(wardrobeService.list(userId, new WardrobeFilter(null, null, null, null, null, "denim"))).isEmpty();
    }

    @Test
    void favoriteFilterSelectsOnlyFavorites() {
        UUID userId = newUser();
        wardrobeService.create(userId, TestData.item("Plain tee", ClothingCategory.TOPS, List.of("white"), List.of()));
        WardrobeItemResponse loved = wardrobeService.create(userId,
                TestData.item("Loved tee", ClothingCategory.TOPS, List.of("black"), List.of()));
        wardrobeService.toggleFavorite(userId, loved.id());

        assertThat(wardrobeService.list(userId, new WardrobeFilter(null, null, null, null, true, null)))
                .extracting(WardrobeItemResponse::name).containsExactly("Loved tee");
        assertThat(wardrobeService.list(userId, new WardrobeFilter(null, null, null, null, false, null)))
                .extracting(WardrobeItemResponse::name).containsExactly("Plain tee");
    }

    @Test
    void updateOnlyAppliesTheFieldsThatWereSent() {
        UUID userId = newUser();
        WardrobeItemResponse created = wardrobeService.create(userId, new CreateItemRequest(
                "Midi skirt", ClothingCategory.BOTTOMS, "midi", List.of("beige"), "solid",
                List.of("minimal"), List.of("fall"), "COS", "M", null, false));

        WardrobeItemResponse updated = wardrobeService.update(userId, created.id(),
                new UpdateItemRequest(null, null, null, List.of("black"), null, null, null, null, null, true));

        assertThat(updated.colors()).containsExactly("black");
        assertThat(updated.favorite()).isTrue();
        // Untouched fields keep their values.
        assertThat(updated.name()).isEqualTo("Midi skirt");
        assertThat(updated.subcategory()).isEqualTo("midi");
        assertThat(updated.brand()).isEqualTo("COS");
        assertThat(updated.size()).isEqualTo("M");
    }

    @Test
    void updateIgnoresABlankName() {
        // A cleared text field must not wipe the item's name.
        UUID userId = newUser();
        WardrobeItemResponse created = wardrobeService.create(userId,
                TestData.item("Ankle boots", ClothingCategory.SHOES, List.of("black"), List.of("edgy")));

        WardrobeItemResponse updated = wardrobeService.update(userId, created.id(),
                new UpdateItemRequest("   ", null, null, null, null, null, null, null, null, null));

        assertThat(updated.name()).isEqualTo("Ankle boots");
    }

    @Test
    void toggleFavoriteFlipsBothWays() {
        UUID userId = newUser();
        WardrobeItemResponse created = wardrobeService.create(userId,
                TestData.item("Shoulder bag", ClothingCategory.BAGS, List.of("tan"), List.of("minimal")));

        assertThat(wardrobeService.toggleFavorite(userId, created.id()).favorite()).isTrue();
        assertThat(wardrobeService.toggleFavorite(userId, created.id()).favorite()).isFalse();
    }

    @Test
    void recentReturnsTheNewestItemsFirstAndRespectsTheLimit() {
        UUID userId = newUser();
        wardrobeService.create(userId, TestData.item("Oldest", ClothingCategory.TOPS, List.of("white"), List.of()));
        wardrobeService.create(userId, TestData.item("Middle", ClothingCategory.TOPS, List.of("white"), List.of()));
        wardrobeService.create(userId, TestData.item("Newest", ClothingCategory.TOPS, List.of("white"), List.of()));

        assertThat(wardrobeService.recent(userId, 2))
                .extracting(WardrobeItemResponse::name).containsExactly("Newest", "Middle");
    }

    @Test
    void recentAlwaysReturnsAtLeastOneItem() {
        // The home screen passes the limit straight through; 0 would render nothing.
        UUID userId = newUser();
        wardrobeService.create(userId, TestData.item("Only item", ClothingCategory.TOPS, List.of("white"), List.of()));

        assertThat(wardrobeService.recent(userId, 0)).hasSize(1);
    }

    @Test
    void deleteRemovesTheItem() {
        UUID userId = newUser();
        WardrobeItemResponse created = wardrobeService.create(userId,
                TestData.item("Gone soon", ClothingCategory.TOPS, List.of("white"), List.of()));

        wardrobeService.delete(userId, created.id());

        assertThat(wardrobeService.list(userId, NO_FILTER)).isEmpty();
        assertThatThrownBy(() -> wardrobeService.get(userId, created.id())).isInstanceOf(ApiException.class);
    }

    @Test
    void anotherUsersItemIsInvisibleAndUntouchable() {
        // Ownership is enforced by the query, and a miss is a 404 — never a hint
        // that the row exists.
        UUID owner = newUser();
        UUID stranger = newUser();
        UUID itemId = wardrobeService.create(owner,
                TestData.item("Private dress", ClothingCategory.DRESSES, List.of("black"), List.of())).id();

        assertThat(wardrobeService.list(stranger, NO_FILTER)).isEmpty();
        assertNotFound(() -> wardrobeService.get(stranger, itemId));
        assertNotFound(() -> wardrobeService.update(stranger, itemId,
                new UpdateItemRequest("Hijacked", null, null, null, null, null, null, null, null, null)));
        assertNotFound(() -> wardrobeService.toggleFavorite(stranger, itemId));
        assertNotFound(() -> wardrobeService.delete(stranger, itemId));
        // similar() checks ownership before it ever reaches the vector query.
        assertNotFound(() -> wardrobeService.similar(stranger, itemId, 5));

        assertThat(wardrobeService.get(owner, itemId).name()).isEqualTo("Private dress");
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void reCataloguingFillsTheTagsAndLeavesTheUsersOwnFieldsAlone() {
        UUID userId = TestData.newUser(authService);
        UUID id = wardrobeService.create(userId, new CreateItemRequest(
                "Kalın yün kazak", ClothingCategory.TOPS, "kazak", List.of("grey"),
                null, List.of(), List.of(), "Zara", "M", null, true)).id();
        when(aiService.parseClothingText(any())).thenReturn(new ClothingAnalysis(
                "top", "kazak", List.of("grey"), List.of(), "solid", List.of("casual"),
                List.of("fall", "winter"), 0.8));

        RetagSummary summary = wardrobeService.retag(userId);

        assertThat(summary.examined()).isEqualTo(1);
        assertThat(summary.updated()).isEqualTo(1);
        assertThat(summary.remaining()).isZero();

        WardrobeItemResponse item = wardrobeService.get(userId, id);
        assertThat(item.seasons()).containsExactly("fall", "winter");
        assertThat(item.styles()).containsExactly("casual");
        // The user's own fields are not the AI's to rewrite.
        assertThat(item.name()).isEqualTo("Kalın yün kazak");
        assertThat(item.brand()).isEqualTo("Zara");
        assertThat(item.size()).isEqualTo("M");
        assertThat(item.favorite()).isTrue();
    }

    @Test
    void reCataloguingSkipsAnItemTheModelCannotRead() {
        UUID userId = TestData.newUser(authService);
        wardrobeService.create(userId,
                TestData.item("qwerty", ClothingCategory.TOPS, List.of(), List.of()));
        when(aiService.parseClothingText(any())).thenReturn(new ClothingAnalysis(
                "unknown", "", List.of(), List.of(), "", List.of(), List.of(), 0.0));

        RetagSummary summary = wardrobeService.retag(userId);

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.updated()).isZero();
    }

    @Test
    void reCataloguingOnlyEverTouchesTheCallersOwnWardrobe() {
        UUID mine = TestData.newUser(authService);
        UUID theirs = TestData.newUser(authService);
        wardrobeService.create(theirs,
                TestData.item("Their coat", ClothingCategory.OUTERWEAR, List.of("black"), List.of()));
        when(aiService.parseClothingText(any())).thenReturn(new ClothingAnalysis(
                "top", "x", List.of(), List.of(), "solid", List.of("casual"), List.of("fall"), 0.8));

        assertThat(wardrobeService.retag(mine).examined()).isZero();
    }

    @Test
    void reCataloguingNeverOverwritesAPatternTheNameCannotKnow() {
        // The parser answers "solid" for any name that does not mention a pattern, so
        // overwriting replaced what the photo showed with a guess.
        UUID userId = TestData.newUser(authService);
        UUID id = wardrobeService.create(userId, new CreateItemRequest(
                "Mavi gömlek", ClothingCategory.TOPS, "gömlek", List.of("blue"),
                "striped", List.of(), List.of(), null, null, null, false)).id();
        when(aiService.parseClothingText(any())).thenReturn(new ClothingAnalysis(
                "top", "gömlek", List.of("blue"), List.of(), "solid", List.of("classic"),
                List.of("spring", "summer"), 0.8));

        wardrobeService.retag(userId);

        WardrobeItemResponse item = wardrobeService.get(userId, id);
        assertThat(item.pattern()).isEqualTo("striped");
        // The tags that a name can genuinely speak to are still refreshed.
        assertThat(item.seasons()).containsExactly("spring", "summer");
    }

    @Test
    void reCataloguingFillsAMissingPattern() {
        UUID userId = TestData.newUser(authService);
        UUID id = wardrobeService.create(userId, new CreateItemRequest(
                "Çizgili gömlek", ClothingCategory.TOPS, "gömlek", List.of("blue"),
                null, List.of(), List.of(), null, null, null, false)).id();
        when(aiService.parseClothingText(any())).thenReturn(new ClothingAnalysis(
                "top", "gömlek", List.of("blue"), List.of(), "striped", List.of("classic"),
                List.of("spring"), 0.8));

        wardrobeService.retag(userId);

        assertThat(wardrobeService.get(userId, id).pattern()).isEqualTo("striped");
    }
}
