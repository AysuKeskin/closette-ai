package ai.closette.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts inside this JVM. Used when no Redis is configured — local development
 * and the test suite, which runs without one.
 *
 * The counts are per-instance and vanish on restart, so this is not a limiter for
 * production: with two backends behind a load balancer each would grant the full
 * budget. It exists so the app runs, and the logic is testable, without Redis.
 */
public class InMemoryCounterStore implements CounterStore {

    private record Entry(long total, Instant expiresAt) {
    }

    private final Map<String, Entry> counters = new ConcurrentHashMap<>();

    @Override
    public long add(String key, long amount, Duration ttl) {
        Instant now = Instant.now();
        return counters.compute(key, (k, existing) -> existing == null || existing.expiresAt().isBefore(now)
                ? new Entry(amount, now.plus(ttl))
                : new Entry(existing.total() + amount, existing.expiresAt())).total();
    }
}
