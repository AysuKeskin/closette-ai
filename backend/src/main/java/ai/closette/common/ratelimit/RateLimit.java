package ai.closette.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as drawing from a rate-limit budget.
 *
 * The cost sits on the endpoint rather than in a config file so that the price of
 * a call is visible where the call is defined — a new AI endpoint that forgets to
 * declare one is then an obvious omission, not a silent free pass.
 *
 * Costs are in units of roughly $0,00005; see the weights in the quota plan.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    RateLimitBucket bucket() default RateLimitBucket.AI;

    /** How much of the budget one call consumes. */
    int cost();
}
