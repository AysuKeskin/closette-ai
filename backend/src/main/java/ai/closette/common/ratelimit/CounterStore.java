package ai.closette.common.ratelimit;

import java.time.Duration;

/**
 * The counting primitive behind the limiter, kept behind an interface so the
 * decision logic can be tested without a Redis running.
 */
public interface CounterStore {

    /**
     * Adds {@code amount} to the counter and returns the new total, setting the
     * expiry on first write so windows clean themselves up.
     *
     * @throws CounterUnavailableException when the store cannot be reached
     */
    long add(String key, long amount, Duration ttl);

    /** Thrown when the backing store is down, so callers can decide what that means. */
    class CounterUnavailableException extends RuntimeException {
        public CounterUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}
