package ai.closette.storage.service;

import ai.closette.auth.service.AuthService;
import ai.closette.common.exception.ApiException;
import ai.closette.storage.model.StoredImage;
import ai.closette.storage.repository.StoredImageRepository;
import ai.closette.support.TestData;
import ai.closette.user.service.UserService;
import ai.closette.wardrobe.service.WardrobeService;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.ai.service.AIService;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

@SpringBootTest(properties = {"closette.storage.cleanup-delay-ms=3600000", "closette.storage.reconcile-delay-ms=3600000"})
@ActiveProfiles("test")
class ImageLifecycleTest {
    @Autowired AuthService auth;
    @Autowired UserService users;
    @Autowired WardrobeService wardrobe;
    @Autowired StorageService storage;
    @Autowired ImageRegistry registry;
    @Autowired ImageCleanup cleanup;
    @Autowired StoredImageRepository images;
    @MockitoBean MinioClient minio;
    @MockitoBean AIService ai;

    private String upload(UUID user) {
        return storage.upload("wardrobe", user, new byte[]{1, 2, 3}, "image/jpeg", "item.jpg");
    }
    private CreateItemRequest item(String key) {
        return new CreateItemRequest("Dress", ClothingCategory.DRESSES, null, List.of(), null, null,
                List.of(), List.of(), null, null, key, false);
    }
    private StoredImage tracked(String key) {
        return images.findAll().stream().filter(i -> key.equals(i.getObjectKey())).findFirst().orElseThrow();
    }
    private void expire(String key) {
        var image = tracked(key);
        image.setDeleteAfter(Instant.now().minusSeconds(1));
        images.save(image);
    }

    @Test
    void anotherUsersKeyCannotBeClaimedOrPresigned() {
        var owner = TestData.newUser(auth);
        var intruder = TestData.newUser(auth);
        String key = upload(owner);
        assertThatThrownBy(() -> wardrobe.create(intruder, item(key))).isInstanceOf(ApiException.class);
        assertThat(storage.presignedOwnedUrl("wardrobe", intruder, key)).isNull();
        assertThat(tracked(key).getDeleteAfter()).isNotNull();
    }

    @Test
    void inventedOrTraversalKeysCannotBeSaved() {
        var user = TestData.newUser(auth);
        assertThatThrownBy(() -> wardrobe.create(user, item(user + "/" + UUID.randomUUID() + ".jpg")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> wardrobe.create(user, item(user + "/../other/file.jpg")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void unsavedUploadExpiresAndIsDeleted() throws Exception {
        String key = upload(TestData.newUser(auth));
        assertThat(tracked(key).getDeleteAfter()).isAfter(Instant.now().plusSeconds(23 * 3600));
        expire(key);
        cleanup.clean();
        assertThat(images.existsByBucketAndObjectKey("wardrobe", key)).isFalse();
        // Scoped to this key: the context shares one database, so a run of clean()
        // also collects whatever other tests left expired, and counting every
        // removeObject would make this assertion depend on the execution order.
        verify(minio).removeObject(argThat((RemoveObjectArgs a) -> key.equals(a.object())));
    }

    @Test
    void expiredUploadCannotBeResurrectedBySavingIt() {
        var user = TestData.newUser(auth);
        String key = upload(user);
        expire(key);
        assertThatThrownBy(() -> wardrobe.create(user, item(key))).isInstanceOf(ApiException.class);
    }

    @Test
    void savedPhotoSurvivesCleanupUntilItsLastReferenceIsDeleted() {
        var user = TestData.newUser(auth);
        String key = upload(user);
        var first = wardrobe.create(user, item(key));
        var second = wardrobe.create(user, item(key));
        assertThat(tracked(key).getDeleteAfter()).isNull();
        wardrobe.delete(user, first.id());
        cleanup.clean();
        assertThat(images.existsByBucketAndObjectKey("wardrobe", key)).isTrue();
        wardrobe.delete(user, second.id());
        cleanup.clean();
        assertThat(images.existsByBucketAndObjectKey("wardrobe", key)).isFalse();
    }

    @Test
    void storageFailureKeepsDurableWorkAndRetries() throws Exception {
        var user = TestData.newUser(auth);
        String key = upload(user);
        var item = wardrobe.create(user, item(key));
        wardrobe.delete(user, item.id());
        doThrow(new RuntimeException("storage unavailable")).when(minio).removeObject(any(RemoveObjectArgs.class));
        cleanup.clean();
        assertThat(images.existsByBucketAndObjectKey("wardrobe", key)).isTrue();
        assertThat(tracked(key).getDeleteAfter()).isAfter(Instant.now());
        doNothing().when(minio).removeObject(any(RemoveObjectArgs.class));
        expire(key);
        cleanup.clean();
        assertThat(images.existsByBucketAndObjectKey("wardrobe", key)).isFalse();
    }

    @Test
    void deletingAnAccountAlsoQueuesUnsavedUploadsAndPreventsNewClaims() {
        var user = TestData.newUser(auth);
        String key = upload(user);
        users.deleteAccount(user);
        assertThat(tracked(key).getDeleteAfter()).isBeforeOrEqualTo(Instant.now());
        assertThatThrownBy(() -> registry.claim(user, "wardrobe", key)).isInstanceOf(ApiException.class);
        cleanup.clean();
        assertThat(images.existsByBucketAndObjectKey("wardrobe", key)).isFalse();
    }

    @Test
    void anUploadFinishingAfterAccountDeletionRecreatesItsCleanupJob() {
        var user = TestData.newUser(auth);
        String key = user + "/" + UUID.randomUUID() + ".jpg";
        registry.prepare(user, "wardrobe", key);
        users.deleteAccount(user);
        cleanup.clean();
        assertThat(images.existsByBucketAndObjectKey("wardrobe", key)).isFalse();
        assertThat(registry.uploaded(user, "wardrobe", key)).isFalse();
        assertThat(tracked(key).getDeleteAfter()).isBeforeOrEqualTo(Instant.now());
        cleanup.clean();
        assertThat(images.existsByBucketAndObjectKey("wardrobe", key)).isFalse();
    }

    @Test
    void legacyOrphanIsDiscoveredAndCleaned() {
        var user = TestData.newUser(auth);
        String key = user + "/" + UUID.randomUUID() + ".jpg";
        registry.discover("wardrobe", key);
        cleanup.clean();
        assertThat(images.existsByBucketAndObjectKey("wardrobe", key)).isFalse();
    }
}
