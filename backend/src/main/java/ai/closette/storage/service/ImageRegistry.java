package ai.closette.storage.service;

import ai.closette.beauty.repository.BeautyItemRepository;
import ai.closette.wardrobe.repository.WardrobeItemRepository;
import ai.closette.wishlist.repository.WishlistItemRepository;
import ai.closette.user.repository.UserRepository;
import ai.closette.storage.model.StoredImage;
import ai.closette.storage.repository.StoredImageRepository;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.config.ClosetteProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ImageRegistry {
    private final StoredImageRepository images;
    private final UserRepository users;
    private final WardrobeItemRepository wardrobe;
    private final BeautyItemRepository beauty;
    private final WishlistItemRepository wishlist;
    private final ClosetteProperties props;

    public ImageRegistry(StoredImageRepository images, UserRepository users,
                         WardrobeItemRepository wardrobe, BeautyItemRepository beauty,
                         WishlistItemRepository wishlist, ClosetteProperties props) {
        this.images = images; this.users = users; this.wardrobe = wardrobe;
        this.beauty = beauty; this.wishlist = wishlist; this.props = props;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void prepare(UUID userId, String bucket, String key) {
        users.lockById(userId).orElseThrow(() -> ApiException.unauthorized(MessageKeys.AUTH_REQUIRED));
        images.save(new StoredImage(userId, bucket, key, Instant.now().plus(24, ChronoUnit.HOURS)));
    }

    @Transactional
    public void claim(UUID userId, String bucket, String key) {
        if (key == null || key.isBlank()) return;
        requireOwnedKey(userId, key);
        users.lockById(userId).orElseThrow(() -> ApiException.unauthorized(MessageKeys.AUTH_REQUIRED));
        StoredImage image = images.lock(bucket, key).orElseThrow(ImageRegistry::missing);
        if (!image.getUserId().equals(userId) || (image.getDeleteAfter() != null
                && !image.getDeleteAfter().isAfter(Instant.now()))) throw missing();
        image.setDeleteAfter(null);
    }

    @Transactional
    public void release(UUID userId, String bucket, String key) {
        if (key == null || key.isBlank()) return;
        requireOwnedKey(userId, key);
        images.lock(bucket, key).ifPresent(image -> image.setDeleteAfter(Instant.now()));
    }

    @Transactional
    public void releaseUser(UUID userId) { images.scheduleUser(userId, Instant.now()); }

    public boolean referenced(StoredImage image) {
        if (!users.existsById(image.getUserId())) return false;
        String key = image.getObjectKey();
        UUID user = image.getUserId();
        if (image.getBucket().equals(props.getStorage().getBucketBeauty())) {
            return beauty.existsByImageKeyAndUserId(key, user);
        }
        return wardrobe.existsByImageKeyAndUserId(key, user) || wishlist.existsByImageKeyAndUserId(key, user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discover(String bucket, String key) {
        if (images.existsByBucketAndObjectKey(bucket, key)) return;
        UUID owner;
        try { owner = UUID.fromString(key.split("/", 2)[0]); }
        catch (IllegalArgumentException e) { return; }
        if (!isOwnedKey(owner, key)) return;
        images.save(new StoredImage(owner, bucket, key, Instant.now()));
    }

    public static boolean isOwnedKey(UUID userId, String key) {
        if (key == null) return false;
        return key.matches(java.util.regex.Pattern.quote(userId.toString())
                + "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(\\.(jpg|jpeg|png|webp|heic|heif|gif))?");
    }
    public static void requireOwnedKey(UUID userId, String key) {
        if (key != null && !key.isBlank() && !isOwnedKey(userId, key)) throw missing();
    }
    private static ApiException missing() { return ApiException.notFound(MessageKeys.ITEM_NOT_FOUND); }
}
