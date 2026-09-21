package ai.closette.storage.model;

import ai.closette.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Survives account deletion so failed object-store deletes remain retryable. */
@Entity
@Table(name = "stored_images", uniqueConstraints = @UniqueConstraint(columnNames = {"bucket", "object_key"}))
public class StoredImage extends BaseEntity {
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private String bucket;
    @Column(name = "object_key", nullable = false, length = 512) private String objectKey;
    private Instant deleteAfter;

    protected StoredImage() { }
    public StoredImage(UUID userId, String bucket, String key, Instant deleteAfter) {
        this.userId = userId;
        this.bucket = bucket;
        this.objectKey = key;
        this.deleteAfter = deleteAfter;
    }
    public UUID getUserId() { return userId; }
    public String getBucket() { return bucket; }
    public String getObjectKey() { return objectKey; }
    public Instant getDeleteAfter() { return deleteAfter; }
    public void setDeleteAfter(Instant value) { deleteAfter = value; }
}
