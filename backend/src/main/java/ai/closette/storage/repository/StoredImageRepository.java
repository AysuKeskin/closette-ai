package ai.closette.storage.repository;

import ai.closette.storage.model.StoredImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface StoredImageRepository extends JpaRepository<StoredImage, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StoredImage i where i.bucket = :bucket and i.objectKey = :key")
    Optional<StoredImage> lock(@Param("bucket") String bucket, @Param("key") String key);

    boolean existsByBucketAndObjectKey(String bucket, String key);
    List<StoredImage> findByDeleteAfterLessThanEqualOrderByDeleteAfterAsc(Instant now, Pageable page);

    @Modifying
    @Query("update StoredImage i set i.deleteAfter = :now where i.userId = :userId")
    void scheduleUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
