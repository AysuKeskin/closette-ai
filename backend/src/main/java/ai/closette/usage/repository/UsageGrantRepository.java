package ai.closette.usage.repository;

import ai.closette.usage.model.AiOperation;
import ai.closette.usage.model.UsageGrant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UsageGrantRepository extends JpaRepository<UsageGrant, UUID> {

    /**
     * The account's live allowances for one operation, oldest period first.
     *
     * Locked, because spending has to read and write the same row: without the
     * lock two devices both see the last unit free and both take it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select g from UsageGrant g
            where g.userId = :userId and g.operation = :operation and g.periodEnd > :now
            order by g.periodEnd asc
            """)
    List<UsageGrant> lockLive(@Param("userId") UUID userId,
                              @Param("operation") AiOperation operation,
                              @Param("now") Instant now);

    /** Same set, for reading a balance without taking a lock. */
    @Query("""
            select g from UsageGrant g
            where g.userId = :userId and g.periodEnd > :now
            """)
    List<UsageGrant> findLive(@Param("userId") UUID userId, @Param("now") Instant now);

    boolean existsByUserIdAndOperationAndSourceAndPeriodStart(
            UUID userId, AiOperation operation, UsageGrant.Source source, Instant periodStart);
}
