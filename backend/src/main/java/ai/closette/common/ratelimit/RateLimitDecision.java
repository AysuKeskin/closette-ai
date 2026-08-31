package ai.closette.common.ratelimit;

import java.time.Duration;

/**
 * The outcome of one budget check.
 *
 * @param allowed  whether the request may proceed
 * @param window   which window ran out ("day", "week", …), null when allowed
 * @param scope    which key ran out ("user", "ip"), null when allowed
 * @param retryAfter how long until that window resets
 */
public record RateLimitDecision(boolean allowed, String window, String scope, Duration retryAfter) {

    private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, null, null, Duration.ZERO);

    public static RateLimitDecision allow() {
        return ALLOWED;
    }

    public static RateLimitDecision deny(String window, String scope, Duration retryAfter) {
        return new RateLimitDecision(false, window, scope, retryAfter);
    }
}
