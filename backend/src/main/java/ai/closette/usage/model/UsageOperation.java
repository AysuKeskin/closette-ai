package ai.closette.usage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to spend an allowance.
 *
 * Recorded before the model is called and closed afterwards, so a phone that
 * loses the reply and retries is charged once, and an attempt that died halfway
 * can be found and given back.
 */
@Entity
@Table(name = "usage_operations")
public class UsageOperation {

    public enum State {
        /** Charged, model not finished. */
        RESERVED,
        /** Finished with a result the user got to keep. */
        SETTLED,
        /** Failed or produced nothing, so the allowance went back. */
        RELEASED
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiOperation operation;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private State state = State.RESERVED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "settled_at")
    private Instant settledAt;

    protected UsageOperation() {
    }

    public UsageOperation(UUID userId, AiOperation operation, String idempotencyKey, int units) {
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.units = units;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AiOperation getOperation() {
        return operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getUnits() {
        return units;
    }

    public State getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void settle() {
        state = State.SETTLED;
        settledAt = Instant.now();
    }

    public void release() {
        state = State.RELEASED;
        settledAt = Instant.now();
    }
}
