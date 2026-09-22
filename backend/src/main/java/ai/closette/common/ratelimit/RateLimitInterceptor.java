package ai.closette.common.ratelimit;

import ai.closette.config.ClosetteProperties.RateLimits;
import ai.closette.config.ClosetteProperties.RateLimits.Window;
import ai.closette.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Charges every {@link RateLimit}-annotated endpoint before it runs.
 *
 * Only the scopes it can see from the request itself — the authenticated user and
 * the client IP. Anything keyed on the request body (a login's email address)
 * belongs in the service, which has already parsed it.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final ai.closette.auth.service.AiConsentService consent;
    private final UserRepository userRepository;

    public RateLimitInterceptor(RateLimiter limiter, UserRepository userRepository, ai.closette.auth.service.AiConsentService consent) {
        this.limiter = limiter;
        this.consent = consent;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimit limit = method.getMethodAnnotation(RateLimit.class);
        if (limit == null) {
            return true;
        }
        String what = request.getMethod() + " " + request.getRequestURI();

        UUID userId = currentUserId();
        if (limit.bucket() == RateLimitBucket.AI && userId != null) consent.require(userId);
        Window byUser = userWindow(limit.bucket(), userId);
        if (byUser != null) {
            charge(limit, "user", userId.toString(), byUser, what, response);
        }

        Window byIp = ipWindow(limit.bucket());
        if (byIp != null) {
            charge(limit, "ip", clientIp(request), byIp, what, response);
        }
        return true;
    }

    private void charge(RateLimit limit, String scope, String key, Window window,
                        String what, HttpServletResponse response) {
        RateLimitDecision decision = limiter.spend(limit.bucket(), scope, key, window, limit.cost());
        if (!decision.allowed()) {
            // Set before applyMode, which throws: the header has to be on the
            // response the error handler then writes.
            response.setHeader("Retry-After", String.valueOf(decision.retryAfter().toSeconds()));
        }
        limiter.applyMode(decision, limit.bucket(), key, what);
    }

    /** Null when this bucket has no per-user budget, or nobody is signed in. */
    private Window userWindow(RateLimitBucket bucket, UUID userId) {
        if (userId == null) {
            return null;
        }
        RateLimits config = limiter.config();
        return switch (bucket) {
            case AI -> isVerified(userId) ? config.getUser() : config.getUnverifiedUser();
            case VERIFY_CODE -> config.getVerifyCode();
            // The auth endpoints are reached signed-out; there is no user to charge.
            case REGISTER, LOGIN, PASSWORD_RESET -> null;
        };
    }

    private Window ipWindow(RateLimitBucket bucket) {
        RateLimits config = limiter.config();
        return switch (bucket) {
            // Signed-out endpoints have no user to charge, so the IP is all there is.
            case REGISTER -> config.getRegisterIp();
            case LOGIN -> config.getLoginIp();
            case PASSWORD_RESET -> config.getPasswordResetIp();
            // Already bounded per user, and a shared IP shouldn't stop someone
            // finishing their own sign-up.
            case VERIFY_CODE -> null;
            // Same reasoning, and it matters more here. Model spending is bounded
            // per user already; an IP ceiling adds no protection against cost, only
            // against each other. Turkish mobile carriers put thousands of real
            // subscribers behind one address, so a few active users would spend the
            // whole street's allowance. Total spend belongs in a global cap, which
            // knows what it is protecting; an IP does not.
            case AI -> null;
        };
    }

    /** The caller, or null for an unauthenticated request. */
    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UUID id ? id : null;
    }

    private boolean isVerified(UUID userId) {
        return userRepository.findById(userId).map(user -> user.isEmailVerified()).orElse(false);
    }

    // Forwarded headers are untrusted input. The private reverse proxy may replace
    // the remote address through the container's explicitly trusted proxy support.
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
