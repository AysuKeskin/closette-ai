package ai.closette.common.ratelimit;

import ai.closette.common.exception.MessageKeys;

/**
 * A named budget. The bucket decides which scopes are checked and which limits
 * apply; the endpoint only says which bucket it draws from and how much it costs.
 */
public enum RateLimitBucket {

    /** Everything that spends model tokens. Checked per user and per IP. */
    AI,

    /** Wrong verification codes. Checked per user. */
    VERIFY_CODE,

    /** Account creation. Checked per IP — there is no user yet. */
    REGISTER,

    /** Sign-in attempts. Checked per IP and, in the service, per email. */
    LOGIN,

    /** Password-reset requests. Every one sends a real email, so it costs money. */
    PASSWORD_RESET;

    /** Config key, e.g. AI → "ai". */
    public String key() {
        return name().toLowerCase().replace('_', '-');
    }

    /**
     * What the user is told. Running out of AI budget is a different situation
     * from being asked to slow down on a sign-in form, and reads differently.
     */
    public String messageKey() {
        return this == AI ? MessageKeys.RATE_LIMIT_EXCEEDED : MessageKeys.TOO_MANY_REQUESTS;
    }
}
