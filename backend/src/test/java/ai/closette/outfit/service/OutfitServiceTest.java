package ai.closette.outfit.service;

import ai.closette.ai.dto.OutfitCandidate;
import ai.closette.ai.dto.OutfitSuggestion;
import ai.closette.ai.service.AIService;
import ai.closette.auth.service.AuthService;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.outfit.dto.OutfitDtos.FeedbackRequest;
import ai.closette.outfit.dto.OutfitDtos.GeneratedLook;
import ai.closette.outfit.dto.OutfitDtos.GetReadyRequest;
import ai.closette.outfit.dto.OutfitDtos.OutfitResponse;
import ai.closette.outfit.dto.OutfitDtos.SaveOutfitRequest;
import ai.closette.outfit.model.FeedbackSignal;
import ai.closette.outfit.model.OutfitStatus;
import ai.closette.outfit.repository.OutfitFeedbackRepository;
import ai.closette.support.TestData;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.service.WardrobeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Get Ready look composition (FR-07/08) and saved-look management (FR-14).
 *
 * The stylist LLM is mocked: without that, these tests would quietly call
 * whatever provider the developer has configured, and a generated rationale
 * would differ every run.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutfitServiceTest {

    @Autowired
    AuthService authService;

    @Autowired
    OutfitService outfitService;

    @Autowired
    WardrobeService wardrobeService;

    @Autowired
    OutfitFeedbackRepository feedbackRepository;

    @MockBean
    AIService aiService;

    /** Default: no stylist available, so the rule-based composer runs. */
    @BeforeEach
    void stylistUnavailable() {
        when(aiService.generateOutfit(any(), any(), any())).thenReturn(null);
    }

    private UUID newUser() {
        return TestData.newUser(authService);
    }

    private UUID addItem(UUID userId, String name, ClothingCategory category) {
        return wardrobeService.create(userId,
                TestData.item(name, category, List.of("black"), List.of("minimal"))).id();
    }

    private static List<String> namesOf(GeneratedLook look) {
        return look.items().stream().map(WardrobeItemResponse::name).toList();
    }

    @Test
    void anEmptyWardrobeGetsGuidanceInsteadOfALook() {
        GeneratedLook look = outfitService.generate(newUser(), new GetReadyRequest("dinner", null));

        assertThat(look.items()).isEmpty();
        assertThat(look.rationale()).contains("Add a few pieces");
    }

    @Test
    void aDressIsPreferredOverATopAndBottom() {
        UUID userId = newUser();
        addItem(userId, "Silk blouse", ClothingCategory.TOPS);
        addItem(userId, "Midi skirt", ClothingCategory.BOTTOMS);
        addItem(userId, "Mini dress", ClothingCategory.DRESSES);

        assertThat(namesOf(outfitService.generate(userId, new GetReadyRequest(null, "dinner"))))
                .containsExactly("Mini dress");
    }

    @Test
    void withoutADressTheLookIsBuiltFromATopAndABottom() {
        UUID userId = newUser();
        addItem(userId, "Silk blouse", ClothingCategory.TOPS);
        addItem(userId, "Midi skirt", ClothingCategory.BOTTOMS);

        assertThat(namesOf(outfitService.generate(userId, new GetReadyRequest(null, "work"))))
                .containsExactlyInAnyOrder("Silk blouse", "Midi skirt");
    }

    @Test
    void theLookIsCompletedWithShoesBagAndOuterwear() {
        // FR-08: a "complete look" means the finishing pieces too, not just the base.
        UUID userId = newUser();
        addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        addItem(userId, "Trench coat", ClothingCategory.OUTERWEAR);
        addItem(userId, "Ankle boots", ClothingCategory.SHOES);
        addItem(userId, "Shoulder bag", ClothingCategory.BAGS);
        addItem(userId, "Gold hoops", ClothingCategory.JEWELRY);

        assertThat(namesOf(outfitService.generate(userId, new GetReadyRequest(null, "dinner"))))
                .containsExactlyInAnyOrder("Mini dress", "Trench coat", "Ankle boots", "Shoulder bag", "Gold hoops");
    }

    @Test
    void onlyOnePieceIsPickedPerCategory() {
        UUID userId = newUser();
        addItem(userId, "Boots A", ClothingCategory.SHOES);
        addItem(userId, "Boots B", ClothingCategory.SHOES);
        addItem(userId, "Mini dress", ClothingCategory.DRESSES);

        List<String> names = namesOf(outfitService.generate(userId, new GetReadyRequest(null, "dinner")));

        assertThat(names).hasSize(2).contains("Mini dress");
        assertThat(names).containsAnyOf("Boots A", "Boots B");
    }

    @Test
    void theRationaleNamesTheOccasionAndThePieceCount() {
        // NFR-07: the user is told why this look, in plain language.
        UUID userId = newUser();
        addItem(userId, "Mini dress", ClothingCategory.DRESSES);

        GeneratedLook look = outfitService.generate(userId, new GetReadyRequest(null, "  a summer wedding  "));

        assertThat(look.rationale()).contains("a summer wedding").contains("1 pieces");
    }

    @Test
    void theFreeTextPromptIsUsedWhenNoOccasionIsGiven() {
        UUID userId = newUser();
        addItem(userId, "Mini dress", ClothingCategory.DRESSES);

        assertThat(outfitService.generate(userId, new GetReadyRequest("brunch with friends", "  ")).rationale())
                .contains("brunch with friends");
    }

    @Test
    void theStylistsLookIsUsedWhenItReturnsOne() {
        UUID userId = newUser();
        UUID dressId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        UUID bootsId = addItem(userId, "Ankle boots", ClothingCategory.SHOES);
        when(aiService.generateOutfit(any(), any(), any())).thenReturn(new OutfitSuggestion(
                List.of(bootsId.toString(), dressId.toString()), "Soft evening", "Because it works."));

        GeneratedLook look = outfitService.generate(userId, new GetReadyRequest(null, "dinner"));

        assertThat(look.title()).isEqualTo("Soft evening");
        assertThat(look.rationale()).isEqualTo("Because it works.");
        // The stylist's ordering is kept — it is part of how the look reads.
        assertThat(namesOf(look)).containsExactly("Ankle boots", "Mini dress");
    }

    @Test
    void theStylistOnlyEverSeesTheUsersOwnItems() {
        // RAG context is the retrieval boundary: another user's wardrobe must
        // never be handed to the model.
        UUID userId = newUser();
        UUID stranger = newUser();
        UUID ownId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        addItem(stranger, "Someone else's coat", ClothingCategory.OUTERWEAR);

        outfitService.generate(userId, new GetReadyRequest(null, "  dinner  "));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OutfitCandidate>> context = ArgumentCaptor.forClass(List.class);
        verify(aiService).generateOutfit(eq("dinner"), context.capture(), any());
        assertThat(context.getValue()).extracting(OutfitCandidate::id)
                .containsExactly(ownId.toString());
    }

    @Test
    void invalidIdsFromTheStylistAreDropped() {
        UUID userId = newUser();
        UUID dressId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        when(aiService.generateOutfit(any(), any(), any())).thenReturn(new OutfitSuggestion(
                List.of(dressId.toString(), "not-a-real-id", dressId.toString()),
                "Soft evening", "Because it works."));

        // Hallucinated and duplicated ids must not reach the user's look.
        assertThat(namesOf(outfitService.generate(userId, new GetReadyRequest(null, "dinner"))))
                .containsExactly("Mini dress");
    }

    @Test
    void aStylistResultWithNoUsableItemsFallsBackToTheRuleBasedComposer() {
        UUID userId = newUser();
        addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        when(aiService.generateOutfit(any(), any(), any())).thenReturn(new OutfitSuggestion(
                List.of("not-a-real-id"), "Soft evening", "Because it works."));

        GeneratedLook look = outfitService.generate(userId, new GetReadyRequest(null, "dinner"));

        assertThat(namesOf(look)).containsExactly("Mini dress");
        assertThat(look.rationale()).contains("built from 1 pieces you already own");
    }

    @Test
    void aStylistLookWithoutWordsStillGetsATitleAndARationale() {
        UUID userId = newUser();
        UUID dressId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        when(aiService.generateOutfit(any(), any(), any()))
                .thenReturn(new OutfitSuggestion(List.of(dressId.toString()), "  ", null));

        GeneratedLook look = outfitService.generate(userId, new GetReadyRequest(null, "dinner"));

        assertThat(look.title()).isEqualTo("Your look");
        assertThat(look.rationale()).contains("dinner");
    }

    @Test
    void theWardrobeContextSentToTheStylistIsCapped() {
        // Cost lever: every extra item is prompt tokens on a paid model.
        UUID userId = newUser();
        for (int i = 0; i < 45; i++) {
            addItem(userId, "Item " + i, ClothingCategory.TOPS);
        }

        outfitService.generate(userId, new GetReadyRequest(null, "dinner"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OutfitCandidate>> context = ArgumentCaptor.forClass(List.class);
        verify(aiService).generateOutfit(any(), context.capture(), any());
        assertThat(context.getValue()).hasSize(40);
    }

    @Test
    void savedOutfitsComeBackWithTheirItems() {
        UUID userId = newUser();
        UUID dressId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        UUID bootsId = addItem(userId, "Ankle boots", ClothingCategory.SHOES);

        OutfitResponse saved = outfitService.save(userId,
                new SaveOutfitRequest("Date night", "dinner", List.of(dressId, bootsId), null));

        assertThat(saved.title()).isEqualTo("Date night");
        assertThat(saved.status()).isEqualTo(OutfitStatus.SAVED); // default when unset
        assertThat(saved.items()).extracting(WardrobeItemResponse::name)
                .containsExactlyInAnyOrder("Mini dress", "Ankle boots");
        assertThat(outfitService.list(userId, null, null)).hasSize(1);
    }

    @Test
    void listFiltersByStatusAndByFavorite() {
        UUID userId = newUser();
        UUID itemId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        OutfitResponse saved = outfitService.save(userId,
                new SaveOutfitRequest("Saved look", "dinner", List.of(itemId), OutfitStatus.SAVED));
        outfitService.save(userId, new SaveOutfitRequest("Worn look", "work", List.of(itemId), OutfitStatus.WORN));
        outfitService.toggleFavorite(userId, saved.id());

        assertThat(outfitService.list(userId, OutfitStatus.WORN, null))
                .extracting(OutfitResponse::title).containsExactly("Worn look");
        assertThat(outfitService.list(userId, null, true))
                .extracting(OutfitResponse::title).containsExactly("Saved look");
    }

    @Test
    void markingALookAsWornRecordsItAsFeedback() {
        // FR-09 feeds on this: wearing a look is the strongest positive signal.
        UUID userId = newUser();
        UUID itemId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        OutfitResponse saved = outfitService.save(userId,
                new SaveOutfitRequest("Date night", "dinner", List.of(itemId), null));

        OutfitResponse worn = outfitService.markWorn(userId, saved.id());

        assertThat(worn.status()).isEqualTo(OutfitStatus.WORN);
        assertThat(feedbackRepository.findByUserId(userId))
                .singleElement()
                .satisfies(fb -> {
                    assertThat(fb.getOutfitId()).isEqualTo(saved.id());
                    assertThat(fb.getSignal()).isEqualTo(FeedbackSignal.WORE);
                });
    }

    @Test
    void feedbackIsStoredAgainstTheOutfit() {
        UUID userId = newUser();
        UUID itemId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        OutfitResponse saved = outfitService.save(userId,
                new SaveOutfitRequest("Date night", "dinner", List.of(itemId), null));

        outfitService.feedback(userId, new FeedbackRequest(saved.id(), FeedbackSignal.LOVE));

        assertThat(feedbackRepository.findByUserId(userId))
                .singleElement()
                .satisfies(fb -> assertThat(fb.getSignal()).isEqualTo(FeedbackSignal.LOVE));
    }

    @Test
    void toggleFavoriteFlipsBothWays() {
        UUID userId = newUser();
        UUID itemId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        OutfitResponse saved = outfitService.save(userId,
                new SaveOutfitRequest("Date night", "dinner", List.of(itemId), null));

        assertThat(outfitService.toggleFavorite(userId, saved.id()).favorite()).isTrue();
        assertThat(outfitService.toggleFavorite(userId, saved.id()).favorite()).isFalse();
    }

    @Test
    void deleteRemovesTheLook() {
        UUID userId = newUser();
        UUID itemId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        OutfitResponse saved = outfitService.save(userId,
                new SaveOutfitRequest("Date night", "dinner", List.of(itemId), null));

        outfitService.delete(userId, saved.id());

        assertThat(outfitService.list(userId, null, null)).isEmpty();
    }

    @Test
    void anotherUsersLookIsInvisibleAndUntouchable() {
        UUID owner = newUser();
        UUID stranger = newUser();
        UUID itemId = addItem(owner, "Mini dress", ClothingCategory.DRESSES);
        OutfitResponse saved = outfitService.save(owner,
                new SaveOutfitRequest("Private look", "dinner", List.of(itemId), null));

        assertThat(outfitService.list(stranger, null, null)).isEmpty();
        assertNotFound(() -> outfitService.toggleFavorite(stranger, saved.id()));
        assertNotFound(() -> outfitService.markWorn(stranger, saved.id()));
        assertNotFound(() -> outfitService.delete(stranger, saved.id()));
    }

    @Test
    void aLookOnlyEverShowsItemsTheOwnerStillHas() {
        // Items can be deleted after a look is saved; the look must not break.
        UUID userId = newUser();
        UUID keptId = addItem(userId, "Mini dress", ClothingCategory.DRESSES);
        UUID removedId = addItem(userId, "Ankle boots", ClothingCategory.SHOES);
        OutfitResponse saved = outfitService.save(userId,
                new SaveOutfitRequest("Date night", "dinner", List.of(keptId, removedId), null));
        wardrobeService.delete(userId, removedId);

        assertThat(outfitService.list(userId, null, null))
                .singleElement()
                .satisfies(outfit -> assertThat(outfit.items())
                        .extracting(WardrobeItemResponse::name).containsExactly("Mini dress"));
        assertThat(saved.items()).hasSize(2);
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
