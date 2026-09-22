package ai.closette.usage.service;

import ai.closette.usage.model.AiOperation;
import ai.closette.usage.model.UsageOperation;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs one AI action against the caller's allowance.
 *
 * The reserve/settle/release dance is identical at every call site and easy to
 * get half right: forgetting the release is a user charged for nothing, and
 * forgetting the reserve is an allowance that never runs out. Having one place
 * do it means a new AI feature cannot quietly skip the part that costs money.
 */
@Component
public class AiUse {

    private final UsageService usage;

    public AiUse(UsageService usage) {
        this.usage = usage;
    }

    /**
     * Charge one use, run the work, and keep the charge only if it produced
     * something.
     *
     * @param idempotencyKey identifies the attempt. The same key from the same
     *                       user is the same attempt, charged once.
     */
    public <T> T run(UUID userId, AiOperation operation, String idempotencyKey, Supplier<T> work) {
        Optional<UsageOperation> reserved = usage.reserve(userId, operation, idempotencyKey);
        T result;
        try {
            result = work.get();
        } catch (RuntimeException e) {
            reserved.ifPresent(usage::release);
            throw e;
        }
        if (result == null) {
            // No result to keep, so nothing to charge for. The provider may still
            // have billed us for the attempt; that is our cost, not the user's.
            reserved.ifPresent(usage::release);
        } else {
            reserved.ifPresent(usage::settle);
        }
        return result;
    }
}
