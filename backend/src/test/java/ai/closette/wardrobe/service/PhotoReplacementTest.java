package ai.closette.wardrobe.service;

import ai.closette.ai.service.AIService;
import ai.closette.auth.service.AuthService;
import ai.closette.common.exception.ApiException;
import ai.closette.storage.service.StorageService;
import ai.closette.support.TestData;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.dto.UpdateItemRequest;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.ClothingCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Changing an item's photo while editing it, and letting go of the old one. */
@SpringBootTest
@ActiveProfiles("test")
class PhotoReplacementTest {

    @Autowired
    AuthService authService;

    @Autowired
    WardrobeService wardrobe;

    @MockitoBean
    StorageService storage;

    @MockitoBean
    AIService aiService;

    @BeforeEach
    void stubBuckets() {
        when(storage.wardrobeBucket()).thenReturn("wardrobe");
        when(storage.beautyBucket()).thenReturn("beauty");
    }

    private static String key(UUID user) {
        return user + "/" + UUID.randomUUID() + ".jpg";
    }

    private WardrobeItemResponse itemWith(UUID user, String imageKey) {
        return wardrobe.create(user, new CreateItemRequest(
                "Navy dress", ClothingCategory.DRESSES, null, List.of("navy"), null, "solid",
                List.of("minimal"), List.of("spring"), null, null, imageKey, false));
    }

    @Test
    void anewPhotoReplacesTheOldOneAndTheOldOneIsReleased() {
        UUID user = TestData.newUser(authService);
        String first = key(user);
        String second = key(user);
        WardrobeItemResponse created = itemWith(user, first);

        wardrobe.update(user, created.id(), new UpdateItemRequest(
                null, null, null, null, null, null, null, null, null, null, second));

        // The item points at the new photo, and the one it replaced is let go, so
        // cleanup can reclaim the space rather than keeping an orphan forever.
        verify(storage).claim("wardrobe", user, second);
        verify(storage).release("wardrobe", user, first);
    }

    @Test
    void anUpdateThatDoesNotMentionAPhotoLeavesItAlone() {
        UUID user = TestData.newUser(authService);
        String only = key(user);
        WardrobeItemResponse created = itemWith(user, only);

        wardrobe.update(user, created.id(), new UpdateItemRequest(
                "Renamed", null, null, null, null, null, null, null, null, null, null));

        assertThat(wardrobe.get(user, created.id()).name()).isEqualTo("Renamed");
        verify(storage, org.mockito.Mockito.never()).release("wardrobe", user, only);
    }

    @Test
    void aKeyBelongingToSomebodyElseIsRefused() {
        UUID user = TestData.newUser(authService);
        UUID intruder = TestData.newUser(authService);
        WardrobeItemResponse created = itemWith(user, key(user));

        assertThatThrownBy(() -> wardrobe.update(user, created.id(), new UpdateItemRequest(
                null, null, null, null, null, null, null, null, null, null, key(intruder))))
                .isInstanceOf(ApiException.class);
    }
}
