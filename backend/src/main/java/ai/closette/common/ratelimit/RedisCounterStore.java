package ai.closette.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Fixed-window counters in Redis: {@code INCRBY} then {@code EXPIRE} on the first
 * write, so a window disappears on its own once it stops being used.
 *
 * A fixed window lets a burst straddle the boundary and briefly reach twice the
 * limit. At this scale that is cheaper to accept than a sliding window is to
 * maintain — the daily and weekly caps are what actually bound the bill.
 */
public class RedisCounterStore implements CounterStore {

    private final StringRedisTemplate redis;

    public RedisCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long add(String key, long amount, Duration ttl) {
        try {
            Long total = redis.opsForValue().increment(key, amount);
            if (total != null && total == amount) {
                redis.expire(key, ttl);
            }
            return total == null ? 0 : total;
        } catch (Exception e) {
            throw new CounterUnavailableException(e);
        }
    }
}
