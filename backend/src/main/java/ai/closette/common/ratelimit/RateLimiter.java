package ai.closette.common.ratelimit;

import ai.closette.common.exception.ApiException;
import ai.closette.config.ClosetteProperties;
import ai.closette.config.ClosetteProperties.RateLimits.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Spends a request's cost against the configured budgets.
 *
 * Two scopes are checked for the AI bucket and both must pass: the per-user
 * budget is what bounds one person's spend, and the per-IP budget is what stops
 * someone opening ten accounts behind one connection. Neither covers the other.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration DAY = Duration.ofDays(1);
    private static final Duration WEEK = Duration.ofDays(7);

    private final CounterStore store;
    private final ClosetteProperties props;

    public RateLimiter(CounterStore store, ClosetteProperties props) {
        this.store = store;
        this.props = props;
    }

    /**
     * Charges {@code cost} to one scope and reports whether it fit.
     *
     * The cost is added before the total is compared, so a request that would
     * overshoot is refused rather than half-charged — and the overshoot still
     * counts, which is what makes a caller that ignores 429s stay locked out.
     */
    public RateLimitDecision spend(RateLimitBucket bucket, String scope, String key, Window limits, int cost) {
        Instant now = Instant.now();
        try {
            RateLimitDecision minute = charge(bucket, scope, key, "minute", limits.getPerMinute(), cost, MINUTE, now);
            if (!minute.allowed()) return minute;

            RateLimitDecision day = charge(bucket, scope, key, "day", limits.getPerDay(), cost, DAY, now);
            if (!day.allowed()) return day;

            return charge(bucket, scope, key, "week", limits.getPerWeek(), cost, WEEK, now);
        } catch (CounterStore.CounterUnavailableException e) {
            return onStoreDown(bucket, e);
        }
    }

    private RateLimitDecision charge(RateLimitBucket bucket, String scope, String key, String window,
                                     int limit, int cost, Duration ttl, Instant now) {
        if (limit <= 0) {
            return RateLimitDecision.allow(); // window disabled
        }
        String counter = "rl:%s:%s:%s:%s:%s".formatted(bucket.key(), scope, key, window, bucketStamp(window, now));
        long total = store.add(counter, cost, ttl);
        return total <= limit
                ? RateLimitDecision.allow()
                : RateLimitDecision.deny(window, scope, resetIn(window, now));
    }

    /**
     * The window a moment falls in. Putting it in the key is what makes the
     * counter reset: the next window is simply a different key.
     */
    private static long bucketStamp(String window, Instant now) {
        return switch (window) {
            case "minute" -> now.getEpochSecond() / 60;
            case "day" -> now.getEpochSecond() / 86_400;
            default -> now.getEpochSecond() / 604_800;
        };
    }

    private static Duration resetIn(String window, Instant now) {
        Instant end = switch (window) {
            case "minute" -> now.truncatedTo(ChronoUnit.MINUTES).plus(MINUTE);
            case "day" -> Instant.ofEpochSecond((now.getEpochSecond() / 86_400 + 1) * 86_400);
            default -> Instant.ofEpochSecond((now.getEpochSecond() / 604_800 + 1) * 604_800);
        };
        return Duration.between(now, end);
    }

    /** Authentication and paid AI calls must not become unlimited during a Redis outage. */
    private RateLimitDecision onStoreDown(RateLimitBucket bucket, Exception e) {
        log.error("Rate-limit store unavailable — refusing {} until quotas can be checked", bucket, e);
        return RateLimitDecision.deny("store", "unavailable", Duration.ofSeconds(30));
    }

    /**
     * Charges a budget and applies the configured mode: in ENFORCE this throws
     * 429, in LOG it only records that it would have. Both the interceptor and
     * the services go through here so the mode means the same thing everywhere.
     *
     * @return the decision, so a caller that can set headers still may
     */
    public RateLimitDecision enforce(RateLimitBucket bucket, String scope, String key,
                                     Window limits, int cost, String what) {
        RateLimitDecision decision = spend(bucket, scope, key, limits, cost);
        applyMode(decision, bucket, key, what);
        return decision;
    }

    /**
     * Turns a decision into a refusal, or into a log line when the limiter is
     * still in LOG mode. Kept separate from {@link #spend} so a caller can react
     * to the decision — setting {@code Retry-After}, say — before this throws.
     */
    public void applyMode(RateLimitDecision decision, RateLimitBucket bucket, String key, String what) {
        if (decision.allowed()) {
            return;
        }
        if (config().getMode() == ClosetteProperties.RateLimits.Mode.LOG) {
            log.warn("Rate limit would have blocked {} on {} ({} window, {} scope)",
                    key, what, decision.window(), decision.scope());
            return;
        }
        log.info("Rate limit blocked {} on {} ({} window)", key, what, decision.window());
        throw ApiException.rateLimited(bucket.messageKey());
    }

    public ClosetteProperties.RateLimits config() {
        return props.getRatelimit();
    }
}
