package ai.closette.recommendation.service;

import ai.closette.auth.service.AuthService;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.outfit.dto.OutfitDtos.FeedbackRequest;
import ai.closette.outfit.dto.OutfitDtos.OutfitResponse;
import ai.closette.outfit.dto.OutfitDtos.SaveOutfitRequest;
import ai.closette.outfit.model.FeedbackSignal;
import ai.closette.outfit.service.OutfitService;
import ai.closette.recommendation.dto.RecommendationDtos.PreferenceEntry;
import ai.closette.recommendation.dto.RecommendationDtos.ShouldIBuyRequest;
import ai.closette.recommendation.dto.RecommendationDtos.ShouldIBuyResponse;
import ai.closette.support.TestData;
import ai.closette.user.repository.UserRepository;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.service.WardrobeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Preference profile (FR-09) and Should I Buy This (FR-10). Both are rule-based,
 * so the score and the explanation behind it are exact, not approximate.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecommendationServiceTest {

    @Autowired
    AuthService authService;

    @Autowired
    RecommendationService recommendationService;

    @Autowired
    WardrobeService wardrobeService;

    @Autowired
    OutfitService outfitService;

    @Autowired
    UserRepository userRepository;

    /** A user allowed through the shopping assistant's verification gate. */
    private UUID verifiedUser() {
        UUID userId = TestData.newUser(authService);
        TestData.markEmailVerified(userRepository, userId);
        return userId;
    }

    private UUID addItem(UUID userId, String name, ClothingCategory category,
                         List<String> colors, List<String> styles) {
        return wardrobeService.create(userId, TestData.item(name, category, colors, styles)).id();
    }

    @Test
    void shoppingAdviceIsGatedOnEmailVerification() {
        UUID unverified = TestData.newUser(authService);

        assertThatThrownBy(() -> recommendationService.shouldIBuy(unverified,
                new ShouldIBuyRequest(ClothingCategory.TOPS, List.of("black"), List.of())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    @Test
    void describingAnItemDegradesGracefullyWhenTheAiIsDown() {
        // The AI parse is unreachable in tests, so attributes can't be extracted — but the
        // feature must still answer (best-effort) and echo back what the user typed.
        UUID userId = verifiedUser();
        addItem(userId, "Black tee", ClothingCategory.TOPS, List.of("black"), List.of("minimal"));

        ShouldIBuyResponse response = recommendationService.shouldIBuyFromDescription(userId, "a black minimal top");

        assertThat(response).isNotNull();
        assertThat(response.detectedLabel()).contains("black minimal top");
        assertThat(response.verdict()).isIn("buy", "maybe", "skip");
    }

    @Test
    void describingAnItemIsAlsoGatedOnEmailVerification() {
        UUID unverified = TestData.newUser(authService);

        assertThatThrownBy(() -> recommendationService.shouldIBuyFromDescription(unverified, "a beige blazer"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    @Test
    void anEmptyWardrobeHasNothingToCompareAgainst() {
        ShouldIBuyResponse response = recommendationService.shouldIBuy(verifiedUser(),
                new ShouldIBuyRequest(ClothingCategory.TOPS, List.of("black"), List.of("minimal")));

        assertThat(response.matchScore()).isZero();
        assertThat(response.matchingItemCount()).isZero();
        assertThat(response.explanation()).contains("wardrobe is empty");
    }

    @Test
    void theScoreIsTheShareOfTheCandidatesTagsAlreadyOwned() {
        // Fit measures the candidate's own colours/styles, not wardrobe size: here one of the
        // candidate's two colours (black) is owned and one (emerald) is not, so 50%.
        UUID userId = verifiedUser();
        addItem(userId, "Black tee", ClothingCategory.TOPS, List.of("black"), List.of("minimal"));
        addItem(userId, "Red skirt", ClothingCategory.BOTTOMS, List.of("red"), List.of("bold"));

        ShouldIBuyResponse response = recommendationService.shouldIBuy(userId,
                new ShouldIBuyRequest(ClothingCategory.BAGS, List.of("black", "emerald"), List.of()));

        assertThat(response.matchingItemCount()).isEqualTo(1); // pairs with the black tee
        assertThat(response.matchScore()).isEqualTo(50); // 1 of the candidate's 2 tags owned
    }

    @Test
    void aSharedStyleCountsAsAMatchEvenWithoutASharedColor() {
        UUID userId = verifiedUser();
        addItem(userId, "Beige coat", ClothingCategory.OUTERWEAR, List.of("beige"), List.of("minimal"));

        ShouldIBuyResponse response = recommendationService.shouldIBuy(userId,
                new ShouldIBuyRequest(ClothingCategory.TOPS, List.of("navy"), List.of("minimal")));

        assertThat(response.matchingItemCount()).isEqualTo(1);
    }

    @Test
    void matchingIgnoresCaseAndPadding() {
        UUID userId = verifiedUser();
        addItem(userId, "Black tee", ClothingCategory.TOPS, List.of("Black"), List.of("Minimal"));

        ShouldIBuyResponse response = recommendationService.shouldIBuy(userId,
                new ShouldIBuyRequest(ClothingCategory.TOPS, List.of("  BLACK  "), List.of()));

        assertThat(response.matchingItemCount()).isEqualTo(1);
    }

    @Test
    void similarItemsAreOnlyThoseInTheSameCategory() {
        // "You already own something like this" is the strongest signal not to buy,
        // so it must not fire on a black bag when the candidate is a black top.
        UUID userId = verifiedUser();
        addItem(userId, "Black tee", ClothingCategory.TOPS, List.of("black"), List.of("minimal"));
        addItem(userId, "Black bag", ClothingCategory.BAGS, List.of("black"), List.of("minimal"));

        ShouldIBuyResponse response = recommendationService.shouldIBuy(userId,
                new ShouldIBuyRequest(ClothingCategory.TOPS, List.of("black"), List.of()));

        assertThat(response.similarItemCount()).isEqualTo(1);
        assertThat(response.similarItems()).extracting(WardrobeItemResponse::name).containsExactly("Black tee");
        assertThat(response.explanation()).contains("1 similar top");
    }

    @Test
    void theExplanationBreaksTheMatchesDownByCategory() {
        UUID userId = verifiedUser();
        addItem(userId, "Black tee", ClothingCategory.TOPS, List.of("black"), List.of());
        addItem(userId, "Black jeans", ClothingCategory.BOTTOMS, List.of("black"), List.of());

        ShouldIBuyResponse response = recommendationService.shouldIBuy(userId,
                new ShouldIBuyRequest(ClothingCategory.SHOES, List.of("black"), List.of()));

        assertThat(response.explanation())
                .contains("pairs with 2 items")
                .contains("1 top")
                .contains("1 bottom");
    }

    @Test
    void nothingMatchingIsSaidPlainly() {
        UUID userId = verifiedUser();
        addItem(userId, "Red skirt", ClothingCategory.BOTTOMS, List.of("red"), List.of("bold"));

        ShouldIBuyResponse response = recommendationService.shouldIBuy(userId,
                new ShouldIBuyRequest(ClothingCategory.TOPS, List.of("sage"), List.of("classic")));

        assertThat(response.matchScore()).isZero();
        assertThat(response.explanation()).contains("doesn't match your palette much");
    }

    @Test
    void lovingALookGivesItsAttributesTheTopWeight() {
        UUID userId = verifiedUser();
        UUID itemId = addItem(userId, "Black dress", ClothingCategory.DRESSES,
                List.of("black"), List.of("minimal"));
        OutfitResponse outfit = outfitService.save(userId,
                new SaveOutfitRequest("Date night", "dinner", null, List.of(itemId), null));
        outfitService.feedback(userId, new FeedbackRequest(outfit.id(), FeedbackSignal.LOVE));

        List<PreferenceEntry> profile = recommendationService.recomputeProfile(userId);

        assertThat(profile).extracting(PreferenceEntry::attribute)
                .containsExactlyInAnyOrder("black", "minimal");
        assertThat(profile).allSatisfy(entry -> assertThat(entry.weight()).isEqualTo(1.0));
    }

    @Test
    void dislikedAttributesAreFlooredAtZeroRatherThanGoingNegative() {
        UUID userId = verifiedUser();
        UUID lovedId = addItem(userId, "Black dress", ClothingCategory.DRESSES,
                List.of("black"), List.of("minimal"));
        UUID dislikedId = addItem(userId, "Neon top", ClothingCategory.TOPS,
                List.of("coral"), List.of("bold"));
        OutfitResponse loved = outfitService.save(userId,
                new SaveOutfitRequest("Loved", "dinner", null, List.of(lovedId), null));
        OutfitResponse disliked = outfitService.save(userId,
                new SaveOutfitRequest("Disliked", "party", null, List.of(dislikedId), null));
        outfitService.feedback(userId, new FeedbackRequest(loved.id(), FeedbackSignal.LOVE));
        outfitService.feedback(userId, new FeedbackRequest(disliked.id(), FeedbackSignal.DISLIKE));

        List<PreferenceEntry> profile = recommendationService.recomputeProfile(userId);

        assertThat(profile).first().satisfies(entry -> assertThat(entry.weight()).isEqualTo(1.0));
        assertThat(profile).filteredOn(entry -> entry.attribute().equals("coral"))
                .singleElement()
                .satisfies(entry -> assertThat(entry.weight()).isZero());
    }

    @Test
    void theProfileIsSortedByWeight() {
        UUID userId = verifiedUser();
        UUID blackId = addItem(userId, "Black dress", ClothingCategory.DRESSES, List.of("black"), List.of());
        UUID navyId = addItem(userId, "Navy coat", ClothingCategory.OUTERWEAR, List.of("navy"), List.of());
        OutfitResponse blackLook = outfitService.save(userId,
                new SaveOutfitRequest("Black", "dinner", null, List.of(blackId), null));
        OutfitResponse navyLook = outfitService.save(userId,
                new SaveOutfitRequest("Navy", "work", null, List.of(navyId), null));
        outfitService.feedback(userId, new FeedbackRequest(blackLook.id(), FeedbackSignal.LOVE));  // +2
        outfitService.feedback(userId, new FeedbackRequest(navyLook.id(), FeedbackSignal.LIKE));   // +1

        List<PreferenceEntry> profile = recommendationService.recomputeProfile(userId);

        assertThat(profile).extracting(PreferenceEntry::attribute).containsExactly("black", "navy");
        assertThat(profile).extracting(PreferenceEntry::weight).containsExactly(1.0, 0.5);
        assertThat(recommendationService.getProfile(userId)).isEqualTo(profile);
    }

    @Test
    void recomputingReplacesThePreviousProfileAndSkipsDeletedLooks() {
        UUID userId = verifiedUser();
        UUID itemId = addItem(userId, "Black dress", ClothingCategory.DRESSES,
                List.of("black"), List.of("minimal"));
        OutfitResponse outfit = outfitService.save(userId,
                new SaveOutfitRequest("Date night", "dinner", null, List.of(itemId), null));
        outfitService.feedback(userId, new FeedbackRequest(outfit.id(), FeedbackSignal.LOVE));
        assertThat(recommendationService.recomputeProfile(userId)).isNotEmpty();

        outfitService.delete(userId, outfit.id());

        // Feedback pointing at a deleted look contributes nothing, and the stale
        // weights from the previous run must not survive.
        assertThat(recommendationService.recomputeProfile(userId)).isEmpty();
        assertThat(recommendationService.getProfile(userId)).isEmpty();
    }

    @Test
    void oneUsersFeedbackNeverShapesAnotherUsersProfile() {
        UUID owner = verifiedUser();
        UUID stranger = verifiedUser();
        UUID itemId = addItem(owner, "Black dress", ClothingCategory.DRESSES,
                List.of("black"), List.of("minimal"));
        OutfitResponse outfit = outfitService.save(owner,
                new SaveOutfitRequest("Date night", "dinner", null, List.of(itemId), null));
        outfitService.feedback(owner, new FeedbackRequest(outfit.id(), FeedbackSignal.LOVE));

        recommendationService.recomputeProfile(owner);

        assertThat(recommendationService.recomputeProfile(stranger)).isEmpty();
    }
}
