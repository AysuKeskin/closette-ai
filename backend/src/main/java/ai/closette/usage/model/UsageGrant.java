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
 * One allowance: how much of one operation an account may spend in one period.
 *
 * {@code used} is moved under a row lock rather than recomputed from the
 * operations table, so two devices spending the last unit at the same instant
 * cannot both succeed.
 */
@Entity
@Table(name = "usage_grants")
public class UsageGrant {

    /** Why this allowance exists. Part of the unique key, so a period is granted once. */
    public enum Source {
        /** The recurring monthly allowance for the account's plan. */
        MONTHLY,
        /** The one-time bonus a newly verified account gets to see what the app does. */
        WELCOME
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiOperation operation;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private int used;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Source source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UsageGrant() {
    }

    public UsageGrant(UUID userId, AiOperation operation, int amount,
                      Instant periodStart, Instant periodEnd, Source source) {
        this.userId = userId;
        this.operation = operation;
        this.amount = amount;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.source = source;
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

    public int getAmount() {
        return amount;
    }

    public int getUsed() {
        return used;
    }

    public int remaining() {
        return Math.max(0, amount - used);
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public Source getSource() {
        return source;
    }

    /** Take units from this allowance. Callers hold the row lock. */
    public void spend(int units) {
        used += units;
    }

    /** Give units back after work that produced nothing. */
    public void refund(int units) {
        used = Math.max(0, used - units);
    }
}
