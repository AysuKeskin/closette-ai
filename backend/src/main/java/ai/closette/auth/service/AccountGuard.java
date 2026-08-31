package ai.closette.auth.service;

import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.user.model.User;
import ai.closette.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reusable gate for actions that require a verified email (soft verification).
 * Call {@link #requireVerifiedEmail} at the top of any service method that
 * should be blocked until the account is verified.
 */
@Component
public class AccountGuard {

    private final UserRepository userRepository;

    public AccountGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void requireVerifiedEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(MessageKeys.USER_NOT_FOUND));
        if (!user.isEmailVerified()) {
            throw ApiException.emailNotVerified(MessageKeys.AUTH_EMAIL_NOT_VERIFIED);
        }
    }
}
