package ai.closette.storage.service;

import ai.closette.storage.repository.StoredImageRepository;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ImageCleanup {
    private static final Logger log = LoggerFactory.getLogger(ImageCleanup.class);
    private final StoredImageRepository images;
    private final ImageRegistry registry;
    private final StorageService storage;
    private final MinioClient client;
    private final TransactionTemplate transaction;

    public ImageCleanup(StoredImageRepository images, ImageRegistry registry, StorageService storage,
                        MinioClient client, PlatformTransactionManager manager) {
        this.images = images; this.registry = registry; this.storage = storage; this.client = client;
        this.transaction = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${closette.storage.cleanup-delay-ms:60000}", initialDelayString = "${closette.storage.cleanup-delay-ms:60000}")
    public void clean() {
        for (var candidate : images.findByDeleteAfterLessThanEqualOrderByDeleteAfterAsc(
                Instant.now(), PageRequest.of(0, 100))) {
            try {
                transaction.executeWithoutResult(status -> images.lock(candidate.getBucket(), candidate.getObjectKey())
                        .ifPresent(image -> {
                            if (image.getDeleteAfter() == null || image.getDeleteAfter().isAfter(Instant.now())) return;
                            if (registry.referenced(image)) {
                                image.setDeleteAfter(null);
                            } else if (storage.delete(image.getBucket(), image.getObjectKey())) {
                                images.delete(image);
                            } else {
                                image.setDeleteAfter(Instant.now().plusSeconds(300));
                            }
                        }));
            } catch (Exception e) {
                log.warn("Image cleanup failed for {}; the durable job remains queued", candidate.getId(), e);
            }
        }
    }

    /** Finds pre-registry orphan uploads and files left by an interrupted upload. */
    @Scheduled(fixedDelayString = "${closette.storage.reconcile-delay-ms:86400000}", initialDelayString = "${closette.storage.reconcile-delay-ms:86400000}")
    public void reconcile() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        for (String bucket : List.of(storage.wardrobeBucket(), storage.beautyBucket())) {
            try {
                for (var result : client.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build())) {
                    var object = result.get();
                    if (object.lastModified().toInstant().isBefore(cutoff)) registry.discover(bucket, object.objectName());
                }
            } catch (Exception e) { log.warn("Image reconciliation failed for bucket {}; will retry", bucket, e); }
        }
    }
}
