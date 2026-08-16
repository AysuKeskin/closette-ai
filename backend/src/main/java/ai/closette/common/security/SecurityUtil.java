package ai.closette.common.security;

import ai.closette.common.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Convenience accessor for the authenticated user's id, which the JWT filter
 * stores as the security principal.
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof UUID id)) {
            throw ApiException.unauthorized("Authentication required");
        }
        return id;
    }
}
