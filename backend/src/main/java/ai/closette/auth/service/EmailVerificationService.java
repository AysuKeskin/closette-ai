package ai.closette.auth.service;

import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.email.EmailSender;
import ai.closette.user.dto.UserResponse;
import ai.closette.user.model.User;
import ai.closette.user.repository.UserRepository;
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

    private static final Duration CODE_TTL = Duration.ofMinutes(15);
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
        userRepository.save(user);
        emailSender.sendVerificationCode(user.getEmail(), code);
    }

    /** Check the submitted code and, if valid, mark the account verified. */
    @Transactional
    public UserResponse verify(UUID userId, String submittedCode) {
        User user = requireUser(userId);
        if (user.isEmailVerified()) {
            return UserResponse.from(user); // idempotent — already done
        }
        String code = user.getVerificationCode();
        Instant expiresAt = user.getVerificationExpiresAt();
        if (code == null || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION,
                    "code: This code has expired — request a new one");
        }
        if (!code.equals(submittedCode == null ? null : submittedCode.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION,
                    "code: That code is incorrect");
        }
        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationExpiresAt(null);
        return UserResponse.from(userRepository.save(user));
    }

    /** Re-send a fresh code, subject to a short cooldown. */
    @Transactional
    public void resend(UUID userId) {
        User user = requireUser(userId);
        if (user.isEmailVerified()) {
            throw ApiException.conflict("Your email is already verified");
        }
        Instant sentAt = user.getVerificationSentAt();
        if (sentAt != null && sentAt.plus(RESEND_COOLDOWN).isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                    "Please wait a moment before requesting another code");
        }
        issue(user);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
