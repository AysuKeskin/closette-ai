package ai.closette.storage.service;

import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import ai.closette.config.ClosetteProperties;

/**
 * Uploads item images to private object storage and mints short-lived signed URLs
 * for reads (NFR-09: photos are never publicly listable).
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final MinioClient client;
    private final ClosetteProperties props;
    private final ImageRegistry registry;
    private final MinioClient publicClient;

    public StorageService(MinioClient client, ClosetteProperties props, ImageRegistry registry) {
        this.client = client;
        this.props = props;
        this.registry = registry;
        var settings = props.getStorage();
        this.publicClient = settings.getPublicEndpoint() == null || settings.getPublicEndpoint().isBlank() ? client
                : MinioClient.builder().endpoint(settings.getPublicEndpoint()).region(settings.getRegion())
                    .credentials(settings.getAccessKey(), settings.getSecretKey()).build();
    }

    /**
     * Stores the given bytes under a random, user-scoped key and returns that key.
     */
    public String upload(String bucket, UUID userId, byte[] bytes, String contentType, String originalName) {
        String ext = extensionOf(originalName);
        String key = userId + "/" + UUID.randomUUID() + ext;
        registry.prepare(userId, bucket, key);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, bytes.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            return key;
        } catch (Exception e) {
            log.error("Failed to upload object to bucket {}", bucket, e);
            throw ApiException.storage(MessageKeys.STORAGE_UPLOAD_FAILED);
        }
    }

    /** False leaves the durable cleanup job queued for retry. */
    public boolean delete(String bucket, String key) {
        if (key == null || key.isBlank()) return true;
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete object {}/{}", bucket, key, e);
            return false;
        }
    }

    /** Downloads the raw bytes for a stored object (e.g. to compute an embedding). */
    public byte[] download(String bucket, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try (InputStream in = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to download object {}/{}", bucket, key, e);
            return null;
        }
    }

    /** Returns a presigned GET URL valid for the configured TTL. */
    public String presignedUrl(String bucket, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(props.getStorage().getSignedUrlTtlMinutes(), TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to presign object {}/{}", bucket, key, e);
            return null;
        }
    }

    public void claim(String bucket, UUID userId, String key) { registry.claim(userId, bucket, key); }
    public void release(String bucket, UUID userId, String key) { registry.release(userId, bucket, key); }
    public void releaseUser(UUID userId) { registry.releaseUser(userId); }

    public String presignedOwnedUrl(String bucket, UUID userId, String key) {
        return ImageRegistry.isOwnedKey(userId, key) ? presignedUrl(bucket, key) : null;
    }

    public String wardrobeBucket() {
        return props.getStorage().getBucketWardrobe();
    }

    public String beautyBucket() {
        return props.getStorage().getBucketBeauty();
    }

    /** Extensions we are willing to put in an object key. */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".gif");

    /**
     * The extension is the only part of the key a client influences, so it is
     * matched against a list rather than sliced out of the filename: taking
     * everything after the last dot carried "/" and ".." from a crafted name
     * straight into the key, letting an upload land outside its owner's prefix.
     */
    private static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(ext) ? ext : "";
    }
}
