package ai.closette.common.ratelimit;

import ai.closette.config.ClosetteProperties;
import ai.closette.config.ClosetteProperties.RateLimits.Window;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spending logic, with the counter store swapped for an in-memory one — no
 * Redis, so this runs in CI like everything else.
 */
class RateLimiterTest {

    private static final Window GENEROUS = new Window(1000, 1000, 1000);

    private RateLimiter limiter(CounterStore store) {
        return new RateLimiter(store, new ClosetteProperties());
    }

    private RateLimiter limiter() {
        return limiter(new InMemoryCounterStore());
    }

    private RateLimitDecision spend(RateLimiter limiter, String key, Window window, int cost) {
        return limiter.spend(RateLimitBucket.AI, "user", key, window, cost);
    }

    @Test
    void spendingUnderTheLimitIsAllowed() {
        RateLimiter limiter = limiter();

        assertThat(spend(limiter, "u1", GENEROUS, 10).allowed()).isTrue();
    }

    @Test
    void theWindowIsSpentByCostNotByRequestCount() {
        // The whole point of weights: ten cheap calls and one expensive one are
        // not the same amount of money.
        RateLimiter limiter = limiter();
        Window window = new Window(10, 100, 100);

        assertThat(spend(limiter, "u1", window, 9).allowed()).isTrue();
        assertThat(spend(limiter, "u1", window, 2).allowed()).isFalse();
    }

    @Test
    void aRequestThatWouldOvershootIsRefusedWhole() {
        RateLimiter limiter = limiter();
        Window window = new Window(10, 100, 100);

        RateLimitDecision decision = spend(limiter, "u1", window, 11);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.window()).isEqualTo("minute");
    }

    @Test
    void theDailyCapBitesEvenWhenTheMinuteIsFine() {
        RateLimiter limiter = limiter();
        Window window = new Window(1000, 20, 1000);

        assertThat(spend(limiter, "u1", window, 20).allowed()).isTrue();
        RateLimitDecision decision = spend(limiter, "u1", window, 1);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.window()).isEqualTo("day");
    }

    @Test
    void theWeeklyCapBitesEvenWhenTheDayIsFine() {
        // This is the window that stops someone spending the daily maximum every
        // day of the week, so it has to be reachable without tripping the others.
        RateLimiter limiter = limiter();
        Window window = new Window(1000, 1000, 30);

        assertThat(spend(limiter, "u1", window, 30).allowed()).isTrue();
        RateLimitDecision decision = spend(limiter, "u1", window, 1);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.window()).isEqualTo("week");
    }

    @Test
    void budgetsAreSeparatePerKey() {
        RateLimiter limiter = limiter();
        Window window = new Window(10, 100, 100);
        spend(limiter, "u1", window, 10);

        assertThat(spend(limiter, "u2", window, 10).allowed()).isTrue();
    }

    @Test
    void scopesDoNotShareACounter() {
        // A user and an IP that happen to have the same key string must not
        // spend each other's budget.
        RateLimiter limiter = limiter();
        Window window = new Window(10, 100, 100);
        limiter.spend(RateLimitBucket.AI, "user", "same", window, 10);

        assertThat(limiter.spend(RateLimitBucket.AI, "ip", "same", window, 10).allowed()).isTrue();
    }

    @Test
    void aZeroLimitDisablesThatWindow() {
        RateLimiter limiter = limiter();
        Window onlyDaily = new Window(0, 5, 0);

        assertThat(spend(limiter, "u1", onlyDaily, 1000).allowed()).isFalse(); // daily still applies
        assertThat(spend(limiter, "u2", onlyDaily, 5).allowed()).isTrue();
    }

    @Test
    void refusalSaysWhenToComeBack() {
        RateLimiter limiter = limiter();
        Window window = new Window(1, 100, 100);
        spend(limiter, "u1", window, 1);

        RateLimitDecision decision = spend(limiter, "u1", window, 1);

        assertThat(decision.retryAfter()).isBetween(Duration.ZERO, Duration.ofMinutes(1));
    }

    @Test
    void aiIsRefusedWhenTheStoreIsDown() {
        // Failing open here would spend real money with no ceiling.
        RateLimiter limiter = limiter(unavailableStore());

        RateLimitDecision decision = limiter.spend(RateLimitBucket.AI, "user", "u1", GENEROUS, 5);

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void verificationIsAllowedWhenTheStoreIsDown() {
        // The opposite call: an infrastructure blip must not lock someone out of
        // their own account.
        RateLimiter limiter = limiter(unavailableStore());

        RateLimitDecision decision = limiter.spend(RateLimitBucket.VERIFY_CODE, "user", "u1", GENEROUS, 1);

        assertThat(decision.allowed()).isTrue();
    }

    private static CounterStore unavailableStore() {
        return (key, amount, ttl) -> {
            throw new CounterStore.CounterUnavailableException(new IllegalStateException("redis down"));
        };
    }
}
