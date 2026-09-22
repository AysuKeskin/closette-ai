package ai.closette.wardrobe.service;

import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.ai.dto.ColorInfo;
import ai.closette.ai.service.AIService;
import ai.closette.auth.service.AuthService;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.storage.service.StorageService;
import ai.closette.support.TestData;
import ai.closette.wardrobe.dto.AnalyzeResponse;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.ClothingCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Flow A step 1: photo → stored image + editable AI analysis. Storage and the AI
 * seam are mocked, so this covers the wiring and the failure behaviour rather
 * than MinIO or the model itself.
 */
@SpringBootTest
@ActiveProfiles("test")
class WardrobeAnalyzeTest {

    private static final ClothingAnalysis ANALYSIS = new ClothingAnalysis(
            "dress", "mini dress", List.of("navy"),
            List.of(new ColorInfo("navy", "#22314E", 100)),
            "solid", List.of("minimal"), List.of("spring"), 0.72);

    @Autowired
    AuthService authService;

    @Autowired
    WardrobeService wardrobeService;

    @MockitoBean
    StorageService storage;

    @MockitoBean
    AIService aiService;

    @BeforeEach
    void stubStorage() {
        when(storage.wardrobeBucket()).thenReturn("wardrobe");
        when(storage.beautyBucket()).thenReturn("beauty");
    }

    private UUID newUser() {
        return TestData.newUser(authService);
    }

    /**
     * An image key the owner check accepts. Keys are namespaced by user id so a
     * request cannot claim a key belonging to somebody else, and a made-up name
     * is rejected before the item is ever saved.
     */
    private static String ownedKey(UUID userId) {
        return userId + "/" + UUID.randomUUID() + ".jpg";
    }

    private static MockMultipartFile photo() {
        return new MockMultipartFile("file", "item.jpg", "image/jpeg", "image-bytes".getBytes());
    }

    @Test
    void analyzeStoresThePhotoAndReturnsTheSuggestionForConfirmation() {
        UUID userId = newUser();
        when(storage.upload(eq("wardrobe"), eq(userId), any(), eq("image/jpeg"), eq("item.jpg")))
                .thenReturn("key-1");
        when(storage.presignedUrl("wardrobe", "key-1")).thenReturn("https://signed/key-1");
        when(aiService.analyzeClothing(any(), any(), any())).thenReturn(ANALYSIS);

        AnalyzeResponse response = wardrobeService.analyze(userId, photo());

        assertThat(response.imageKey()).isEqualTo("key-1");
        assertThat(response.imageUrl()).isEqualTo("https://signed/key-1");
        assertThat(response.analysis().category()).isEqualTo("dress");
        assertThat(response.analysis().colorDetails()).hasSize(1);
    }

    @Test
    void analyzeRejectsAnEmptyUpload() {
        UUID userId = newUser();
        MockMultipartFile empty = new MockMultipartFile("file", "item.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> wardrobeService.analyze(userId, empty))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void itemsWithoutAPhotoSkipTheEmbeddingEntirely() {
        UUID userId = newUser();

        wardrobeService.create(userId, TestData.item("No photo", ClothingCategory.TOPS, List.of("black"), List.of()));

        verify(aiService, org.mockito.Mockito.never()).embedItem(any(), any(), any());
    }

    @Test
    void aFailingEmbeddingDoesNotBlockSavingTheItem() {
        // Visual similarity is a nice-to-have; losing it must never cost the user
        // the item they just added.
        UUID userId = newUser();
        String key = ownedKey(userId);
        when(storage.download("wardrobe", key)).thenReturn("image-bytes".getBytes());
        when(aiService.embedItem(any(), any(), any())).thenThrow(new IllegalStateException("model down"));

        WardrobeItemResponse created = wardrobeService.create(userId, new CreateItemRequest(
                "Navy dress", ClothingCategory.DRESSES, null, List.of("navy"), null, "solid",
                List.of("minimal"), List.of("spring"), null, null, key, false));

        assertThat(created.id()).isNotNull();
        assertThat(wardrobeService.get(userId, created.id()).name()).isEqualTo("Navy dress");
    }

    @Test
    void anUnreadableStoredImageIsToleratedToo() {
        UUID userId = newUser();
        String key = ownedKey(userId);
        when(storage.download("wardrobe", key)).thenReturn(null);

        assertThatCode(() -> wardrobeService.create(userId, new CreateItemRequest(
                "Navy dress", ClothingCategory.DRESSES, null, List.of("navy"), null, "solid",
                List.of("minimal"), List.of("spring"), null, null, key, false)))
                .doesNotThrowAnyException();
        verify(aiService, org.mockito.Mockito.never()).embedItem(any(), any(), any());
    }
}
