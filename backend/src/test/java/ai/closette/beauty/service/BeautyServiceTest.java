package ai.closette.beauty.service;

import ai.closette.ai.dto.BeautyAnalysis;
import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.ai.service.AIService;
import ai.closette.auth.service.AuthService;
import ai.closette.beauty.dto.BeautyAnalyzeResponse;
import ai.closette.beauty.dto.BeautyItemResponse;
import ai.closette.beauty.dto.CreateBeautyItemRequest;
import ai.closette.beauty.dto.UpdateBeautyItemRequest;
import ai.closette.beauty.model.BeautyCategory;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.storage.service.StorageService;
import ai.closette.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Beauty inventory (FR-04/05/06). Storage and the AI seam are mocked so Flow B
 * can be exercised without MinIO or the FastAPI service running.
 */
@SpringBootTest
@ActiveProfiles("test")
class BeautyServiceTest {

    @Autowired
    AuthService authService;

    @Autowired
    BeautyService beautyService;

    @MockitoBean
    StorageService storage;

    @MockitoBean
    AIService aiService;

    @BeforeEach
    void stubStorage() {
        when(storage.beautyBucket()).thenReturn("beauty");
        when(storage.wardrobeBucket()).thenReturn("wardrobe");
    }

    private UUID newUser() {
        return TestData.newUser(authService);
    }

    private static CreateBeautyItemRequest request(String productName, BeautyCategory category) {
        return new CreateBeautyItemRequest("CeraVe", productName, category, null, null, "50ml",
                List.of("niacinamide", "ceramide"), LocalDate.of(2026, 1, 5), null, null, 12, 80, false);
    }

    @Test
    void savedProductsKeepTheirDetails() {
        UUID userId = newUser();

        BeautyItemResponse created = beautyService.create(userId, request("Moisturizing Cream", BeautyCategory.SKINCARE));

        assertThat(created.productName()).isEqualTo("Moisturizing Cream");
        assertThat(created.brand()).isEqualTo("CeraVe");
        assertThat(created.ingredients()).containsExactly("niacinamide", "ceramide");
        assertThat(created.paoMonths()).isEqualTo(12);
        assertThat(created.amountRemaining()).isEqualTo(80);
        assertThat(created.purchaseDate()).isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void blankOptionalFieldsAreStoredAsNull() {
        UUID userId = newUser();

        BeautyItemResponse created = beautyService.create(userId, new CreateBeautyItemRequest(
                "   ", "  Lip Balm  ", BeautyCategory.MAKEUP, "  ", "  ", "  ",
                null, null, null, null, null, null, null));

        assertThat(created.productName()).isEqualTo("Lip Balm");
        assertThat(created.imageUrl()).isNull();
        assertThat(created.brand()).isNull();
        assertThat(created.size()).isNull();
        assertThat(created.favorite()).isFalse();
    }

    @Test
    void listFiltersByCategory() {
        UUID userId = newUser();
        beautyService.create(userId, request("Moisturizing Cream", BeautyCategory.SKINCARE));
        beautyService.create(userId, request("Lip Balm", BeautyCategory.MAKEUP));

        assertThat(beautyService.list(userId, null, null)).hasSize(2);
        assertThat(beautyService.list(userId, BeautyCategory.MAKEUP, null))
                .extracting(BeautyItemResponse::productName).containsExactly("Lip Balm");
        assertThat(beautyService.list(userId, BeautyCategory.PERFUME, null)).isEmpty();
    }

    @Test
    void favouritingAProductFlipsBothWays() {
        UUID userId = newUser();
        BeautyItemResponse created = beautyService.create(userId, request("Lip Balm", BeautyCategory.MAKEUP));

        assertThat(beautyService.toggleFavorite(userId, created.id()).favorite()).isTrue();
        assertThat(beautyService.toggleFavorite(userId, created.id()).favorite()).isFalse();
    }

    @Test
    void theFavouritesFilterCombinesWithTheCategoryFilter() {
        UUID userId = newUser();
        BeautyItemResponse cream = beautyService.create(userId, request("Cream", BeautyCategory.SKINCARE));
        beautyService.create(userId, request("Lip Balm", BeautyCategory.MAKEUP));
        beautyService.toggleFavorite(userId, cream.id());

        assertThat(beautyService.list(userId, null, true))
                .extracting(BeautyItemResponse::productName).containsExactly("Cream");
        assertThat(beautyService.list(userId, BeautyCategory.SKINCARE, true))
                .extracting(BeautyItemResponse::productName).containsExactly("Cream");
        assertThat(beautyService.list(userId, BeautyCategory.MAKEUP, true)).isEmpty();
        // A false/absent flag must not silently mean "favourites only".
        assertThat(beautyService.list(userId, null, false)).hasSize(2);
    }

    @Test
    void anotherUsersProductCannotBeFavourited() {
        UUID owner = newUser();
        UUID stranger = newUser();
        BeautyItemResponse created = beautyService.create(owner, request("Lip Balm", BeautyCategory.MAKEUP));

        assertNotFound(() -> beautyService.toggleFavorite(stranger, created.id()));
    }

    @Test
    void deleteRemovesTheProduct() {
        UUID userId = newUser();
        BeautyItemResponse created = beautyService.create(userId, request("Lip Balm", BeautyCategory.MAKEUP));

        beautyService.delete(userId, created.id());

        assertThat(beautyService.list(userId, null, null)).isEmpty();
    }

    @Test
    void anotherUsersProductIsInvisibleAndUntouchable() {
        UUID owner = newUser();
        UUID stranger = newUser();
        BeautyItemResponse created = beautyService.create(owner, request("Lip Balm", BeautyCategory.MAKEUP));

        assertThat(beautyService.list(stranger, null, null)).isEmpty();
        assertNotFound(() -> beautyService.get(stranger, created.id()));
        assertNotFound(() -> beautyService.delete(stranger, created.id()));
        assertThat(beautyService.get(owner, created.id()).productName()).isEqualTo("Lip Balm");
    }

    @Test
    void analyzeStoresThePhotoInTheBeautyBucketAndReturnsAnEditableResult() {
        UUID userId = newUser();
        MockMultipartFile photo = new MockMultipartFile("file", "cream.jpg", "image/jpeg", "bytes".getBytes());
        when(storage.upload(eq("beauty"), eq(userId), any(), any(), any())).thenReturn("key-1");
        when(storage.presignedUrl("beauty", "key-1")).thenReturn("https://signed/key-1");
        when(aiService.analyzeBeautyPhoto(any(), any(), any()))
                .thenReturn(new BeautyAnalysis("CeraVe", "Hydrating Cleanser", "skincare", 0.61));

        BeautyAnalyzeResponse response = beautyService.analyze(userId, photo);

        assertThat(response.imageKey()).isEqualTo("key-1");
        assertThat(response.imageUrl()).isEqualTo("https://signed/key-1");
        assertThat(response.analysis().productName()).isEqualTo("Hydrating Cleanser");
    }

    @Test
    void analyzeRejectsAnEmptyUpload() {
        UUID userId = newUser();
        MockMultipartFile empty = new MockMultipartFile("file", "cream.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> beautyService.analyze(userId, empty))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void ingredientLookupIsTrimmedBeforeItReachesTheAiSeam() {
        UUID userId = newUser();
        when(aiService.explainIngredient("Niacinamide"))
                .thenReturn(new IngredientExplanation("Niacinamide", "A form of vitamin B3."));

        assertThat(beautyService.explainIngredient(userId, "  Niacinamide  ").explanation())
                .isEqualTo("A form of vitamin B3.");
        verify(aiService).explainIngredient("Niacinamide");
    }

    @Test
    void ingredientLookupRejectsABlankName() {
        UUID userId = newUser();
        assertThatThrownBy(() -> beautyService.explainIngredient(userId, "   "))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION));
        assertThatThrownBy(() -> beautyService.explainIngredient(userId, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updatingAProductChangesOnlyTheProvidedFields() {
        UUID userId = newUser();
        BeautyItemResponse created = beautyService.create(userId, request("Cream", BeautyCategory.SKINCARE));

        BeautyItemResponse updated = beautyService.update(userId, created.id(),
                new UpdateBeautyItemRequest(null, "Rich Cream", null, "100ml", null, true, null));

        assertThat(updated.productName()).isEqualTo("Rich Cream");
        assertThat(updated.size()).isEqualTo("100ml");
        assertThat(updated.favorite()).isTrue();
        assertThat(updated.brand()).isEqualTo("CeraVe"); // untouched
    }

    @Test
    void updatingAnotherUsersProductIsNotFound() {
        UUID owner = newUser();
        UUID stranger = newUser();
        BeautyItemResponse created = beautyService.create(owner, request("Cream", BeautyCategory.SKINCARE));

        assertNotFound(() -> beautyService.update(stranger, created.id(),
                new UpdateBeautyItemRequest(null, "Hijacked", null, null, null, null, null)));
    }

    @Test
    void scanningIngredientsCleansWhatTheAiRead() {
        UUID userId = newUser();
        when(aiService.extractIngredients(any(), any(), any()))
                .thenReturn(List.of("Aqua", ".", "and", "( Niacinamide )"));

        List<String> scanned = beautyService.scanIngredients(userId, 
                new MockMultipartFile("file", "list.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(scanned).containsExactly("Aqua", "Niacinamide");
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
