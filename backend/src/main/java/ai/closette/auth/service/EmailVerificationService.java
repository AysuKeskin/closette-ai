package ai.closette.auth.service;

import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.common.exception.ErrorCode;
import ai.closette.email.EmailSender;
import ai.closette.user.dto.UserResponse;
import ai.closette.user.model.User;
import ai.closette.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Soft email verification (FR-01 hardening): an account is usable immediately,
 * but stays unverified until the emailed 6-digit code is entered. The code is
 * delivered through the provider-independent {@link EmailSender}.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    /** Wrong codes allowed before the code is burned. Six digits over a 15-minute
     *  window is guessable if the guessing is free; this makes it cost a resend. */
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailSender emailSender;

    public EmailVerificationService(UserRepository userRepository, EmailSender emailSender) {
        this.userRepository = userRepository;
        this.emailSender = emailSender;
    }

    /** Generate a fresh code, persist it on the user, and "send" it. */
    @Transactional
    public void issue(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        user.setVerificationCode(code);
        user.setVerificationExpiresAt(now.plus(CODE_TTL));
        user.setVerificationSentAt(now);
        user.setVerificationAttempts(0); // a fresh code starts with a fresh budget
        userRepository.save(user);
        emailSender.sendVerificationCode(user.getEmail(), code);
    }

    /**
     * Check the submitted code and, if valid, mark the account verified.
     *
     * A wrong code both increments the attempt counter and throws — so the
     * exception must not roll the increment back, or the counter resets on every
     * guess and the limit never bites.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public UserResponse verify(UUID userId, String submittedCode) {
        User user = requireUser(userId);
        if (user.isEmailVerified()) {
            return UserResponse.from(user); // idempotent — already done
        }
        String code = user.getVerificationCode();
        Instant expiresAt = user.getVerificationExpiresAt();
        if (code == null || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw ApiException.validation(MessageKeys.AUTH_CODE_EXPIRED);
        }
        if (!code.equals(submittedCode == null ? null : submittedCode.trim())) {
            int attempts = user.getVerificationAttempts() + 1;
            user.setVerificationAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                // Burn the code rather than lock the account: the user is one
                // resend away, an attacker is back to guessing a new secret.
                user.setVerificationCode(null);
                user.setVerificationExpiresAt(null);
                userRepository.save(user);
                log.warn("Verification code burned after {} wrong attempts for user {}", attempts, userId);
                throw ApiException.validation(MessageKeys.AUTH_TOO_MANY_ATTEMPTS);
            }
            userRepository.save(user);
            throw ApiException.validation(MessageKeys.AUTH_CODE_INCORRECT);
        }
        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationExpiresAt(null);
        user.setVerificationAttempts(0);
        return UserResponse.from(userRepository.save(user));
    }

    /** Re-send a fresh code, subject to a short cooldown. */
    @Transactional
    public void resend(UUID userId) {
        User user = requireUser(userId);
        if (user.isEmailVerified()) {
            throw ApiException.conflict(MessageKeys.AUTH_EMAIL_ALREADY_VERIFIED);
        }
        Instant sentAt = user.getVerificationSentAt();
        if (sentAt != null && sentAt.plus(RESEND_COOLDOWN).isAfter(Instant.now())) {
            throw ApiException.rateLimited(MessageKeys.AUTH_RESEND_COOLDOWN);
        }
        issue(user);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(MessageKeys.USER_NOT_FOUND));
    }
}
