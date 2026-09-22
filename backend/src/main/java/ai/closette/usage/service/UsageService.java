package ai.closette.usage.service;

import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.usage.UsageProperties;
import ai.closette.usage.model.AiOperation;
import ai.closette.usage.model.UsageGrant;
import ai.closette.usage.model.UsageOperation;
import ai.closette.usage.model.UserPlan;
import ai.closette.usage.repository.UsageGrantRepository;
import ai.closette.usage.repository.UsageOperationRepository;
import ai.closette.user.model.User;
import ai.closette.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What an account may still spend on AI, and the spending of it.
 *
 * The rate limiter next door guards against bursts and forgets everything on
 * restart. This is the other thing: an entitlement, counted in Postgres under a
 * row lock, because an allowance somebody paid for cannot evaporate when a cache
 * restarts and cannot be spent twice by two phones at once.
 *
 * Spending is in two steps. {@link #reserve} charges before the model is called;
 * {@link #settle} closes it once there is a result worth keeping, and
 * {@link #release} gives it back when there is not. Charging afterwards would let
 * a user start ten requests against their last unit.
 */
@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);

    private final UsageGrantRepository grants;
    private final UsageOperationRepository operations;
    private final UserRepository users;
    private final UsageProperties config;

    public UsageService(UsageGrantRepository grants, UsageOperationRepository operations,
                        UserRepository users, UsageProperties config) {
        this.grants = grants;
        this.operations = operations;
        this.users = users;
        this.config = config;
    }

    /** One operation's standing: what was granted, what is left, and when it renews. */
    public record Balance(AiOperation operation, int remaining, int total, Instant resetsAt) {
    }

    /**
     * Charge one use before the work starts.
     *
     * @param idempotencyKey identifies the attempt, not the request. A phone that
     *                       loses the reply and retries sends the same key and is
     *                       charged once.
     * @return the operation to settle or release, or empty when the allowance is
     *         spent and the mode only logs.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UsageOperation> reserve(UUID userId, AiOperation operation, String idempotencyKey) {
        Optional<UsageOperation> existing = operations.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            // The same attempt arriving twice. Already paid for; do not charge again.
            return existing;
        }

        ensureMonthlyGrants(userId);
        Instant now = Instant.now();
        List<UsageGrant> live = grants.lockLive(userId, operation, now);

        // Spend the allowance that expires first, so a welcome bonus is used before
        // it lapses rather than sitting behind a grant that renews anyway.
        Optional<UsageGrant> payer = live.stream().filter(g -> g.remaining() > 0).findFirst();
        if (payer.isEmpty()) {
            int granted = live.stream().mapToInt(UsageGrant::getAmount).sum();
            if (config.getMode() == UsageProperties.Mode.ENFORCE) {
                log.info("Usage refused for user {} on {} (allowance {} spent)", userId, operation, granted);
                throw ApiException.quotaExhausted(MessageKeys.AI_QUOTA_EXHAUSTED);
            }
            // Shadow mode: report what enforcement would have cost this person, and
            // let the work through. The numbers are a proposal until months of this
            // line say whether they are livable.
            log.info("Usage would have been refused for user {} on {} (allowance {} spent)",
                    userId, operation, granted);
            return Optional.empty();
        }

        UsageGrant grant = payer.get();
        grant.spend(1);
        grants.save(grant);
        return Optional.of(operations.save(new UsageOperation(userId, operation, idempotencyKey, 1)));
    }

    /** The work produced something the user keeps. The charge stands. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settle(UsageOperation operation) {
        if (operation.getState() != UsageOperation.State.RESERVED) return;
        operation.settle();
        operations.save(operation);
    }

    /**
     * The work produced nothing, so the allowance goes back.
     *
     * Only the user's allowance: whatever the provider billed us for the failed
     * attempt was still spent, and pretending otherwise would hide the cost of a
     * flaky model behind a clean-looking quota.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UsageOperation operation) {
        if (operation.getState() != UsageOperation.State.RESERVED) return;
        Instant now = Instant.now();
        grants.lockLive(operation.getUserId(), operation.getOperation(), now).stream()
                .filter(g -> g.getUsed() > 0)
                .findFirst()
                .ifPresent(g -> {
                    g.refund(operation.getUnits());
                    grants.save(g);
                });
        operation.release();
        operations.save(operation);
    }

    /** Everything the account may still spend, for the app to show before it asks. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Balance> balances(UUID userId) {
        ensureMonthlyGrants(userId);
        Instant now = Instant.now();
        Map<AiOperation, List<UsageGrant>> byOperation = new EnumMap<>(AiOperation.class);
        for (UsageGrant g : grants.findLive(userId, now)) {
            byOperation.computeIfAbsent(g.getOperation(), k -> new ArrayList<>()).add(g);
        }
        List<Balance> out = new ArrayList<>();
        for (AiOperation operation : AiOperation.values()) {
            List<UsageGrant> live = byOperation.getOrDefault(operation, List.of());
            int remaining = live.stream().mapToInt(UsageGrant::remaining).sum();
            int total = live.stream().mapToInt(UsageGrant::getAmount).sum();
            // The monthly grant is what renews; a welcome bonus expiring sooner is
            // not a reset, so it must not be shown as one.
            Instant resets = live.stream()
                    .filter(g -> g.getSource() == UsageGrant.Source.MONTHLY)
                    .map(UsageGrant::getPeriodEnd)
                    .findFirst()
                    .orElse(monthEnd(now));
            out.add(new Balance(operation, remaining, total, resets));
        }
        return out;
    }

    /**
     * Make sure this calendar month has been granted, exactly once.
     *
     * The unique key on (user, operation, source, period start) is what enforces
     * "once": two requests racing at the turn of the month both try, one loses,
     * and the loser simply reads what the winner wrote.
     */
    private void ensureMonthlyGrants(UUID userId) {
        User user = users.findById(userId).orElse(null);
        if (user == null) return;
        Instant start = monthStart(Instant.now());
        Instant end = monthEnd(Instant.now());
        Map<AiOperation, Integer> allowance = config.monthlyFor(planOf(user));
        for (Map.Entry<AiOperation, Integer> entry : allowance.entrySet()) {
            if (entry.getValue() <= 0) continue;
            if (grants.existsByUserIdAndOperationAndSourceAndPeriodStart(
                    userId, entry.getKey(), UsageGrant.Source.MONTHLY, start)) {
                continue;
            }
            try {
                grants.save(new UsageGrant(userId, entry.getKey(), entry.getValue(),
                        start, end, UsageGrant.Source.MONTHLY));
            } catch (RuntimeException e) {
                // Lost the race to another request for the same month; the row the
                // winner wrote is the one both will spend from.
                log.debug("Monthly grant for {} on {} already existed", userId, entry.getKey());
            }
        }
    }

    /**
     * The one-time bonus, given when an account verifies its email.
     *
     * Verification first, deliberately: a bonus handed out before it costs
     * something to obtain an account is a bonus handed out repeatedly.
     */
    @Transactional
    public void grantWelcome(UUID userId) {
        Instant start = Instant.now();
        Instant end = start.plus(config.getWelcome().getDays(), ChronoUnit.DAYS);

        // Asked once, for the whole bonus. Asking per operation would see the first
        // grant this loop wrote and stop, leaving an account welcomed to photos and
        // nothing else.
        boolean alreadyWelcomed = grants.findLive(userId, start).stream()
                .anyMatch(g -> g.getSource() == UsageGrant.Source.WELCOME);
        if (alreadyWelcomed) return;

        for (Map.Entry<AiOperation, Integer> entry : config.getWelcome().asMap().entrySet()) {
            if (entry.getValue() <= 0) continue;
            grants.save(new UsageGrant(userId, entry.getKey(), entry.getValue(),
                    start, end, UsageGrant.Source.WELCOME));
        }
    }

    /**
     * Names one attempt, from something that is already unique to it.
     *
     * For a photo the storage key works: uploading the same picture again produces
     * a new key and is rightly a new analysis, while a phone that lost the reply
     * and retries sends the same one and is charged once. Until the app sends its
     * own key, this is the closest honest answer the server can give.
     */
    public static String attemptKey(UUID userId, String kind, String discriminator) {
        return kind + ":" + Integer.toHexString((userId + "|" + discriminator).hashCode());
    }

    private static UserPlan planOf(User user) {
        return user.getPlan() == null ? UserPlan.FREE : user.getPlan();
    }

    private static Instant monthStart(Instant now) {
        return YearMonth.from(now.atZone(ZoneOffset.UTC)).atDay(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant monthEnd(Instant now) {
        return YearMonth.from(now.atZone(ZoneOffset.UTC)).plusMonths(1).atDay(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
