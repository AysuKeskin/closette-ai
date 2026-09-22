package ai.closette.usage.model;

/**
 * What an account is entitled to. Stored on the user so quota code has one place
 * to ask; billing will set it when subscriptions exist, and until then every
 * account is FREE.
 */
public enum UserPlan {
    FREE,
    PLUS
}
