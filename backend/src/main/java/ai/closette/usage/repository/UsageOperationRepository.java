package ai.closette.usage.repository;

import ai.closette.usage.model.UsageOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsageOperationRepository extends JpaRepository<UsageOperation, UUID> {

    /** The idempotency lookup: the same key from the same user is the same attempt. */
    Optional<UsageOperation> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
