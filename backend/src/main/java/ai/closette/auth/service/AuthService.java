package ai.closette.auth.service;

import ai.closette.auth.dto.AuthResponse;
import ai.closette.auth.dto.ForgotPasswordRequest;
import ai.closette.auth.dto.LoginRequest;
import ai.closette.auth.dto.RefreshRequest;
import ai.closette.auth.dto.RegisterRequest;
import ai.closette.auth.dto.ResetPasswordRequest;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.common.ratelimit.RateLimitBucket;
import ai.closette.common.ratelimit.RateLimiter;
import ai.closette.common.exception.ErrorCode;
import ai.closette.email.EmailSender;
import ai.closette.user.model.User;
import ai.closette.user.repository.UserRepository;
import ai.closette.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final Duration RESET_TTL = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final EmailVerificationService emailVerificationService;
    private final EmailSender emailSender;
    private final RateLimiter rateLimiter;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       SessionService sessions,
                       EmailVerificationService emailVerificationService,
                       EmailSender emailSender,
                       RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.emailVerificationService = emailVerificationService;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String username = request.username().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict(MessageKeys.AUTH_EMAIL_TAKEN);
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict(MessageKeys.AUTH_USERNAME_TAKEN);
        }
        User user = new User(
                email,
                username,
                passwordEncoder.encode(request.password()),
                safeName(request.displayName()));
        user = userRepository.save(user);
        // Soft verification: account is usable now; email a code to verify later.
        emailVerificationService.issue(user);
        return sessions.issue(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        rateLimiter.enforce(RateLimitBucket.LOGIN, "email", email,
                rateLimiter.config().getLoginEmail(), 1, "login");
        User user = userRepository.lockByEmail(request.email().trim())
                .orElseThrow(() -> ApiException.unauthorized(MessageKeys.AUTH_INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized(MessageKeys.AUTH_INVALID_CREDENTIALS);
        }
        return sessions.issue(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        return sessions.rotate(request.refreshToken());
    }

    public void logout(RefreshRequest request) {
        sessions.logout(request.refreshToken());
    }

    /**
     * Start a password reset. Always succeeds from the caller's view (we never
     * reveal whether an email is registered); a code is only sent if it exists.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim();
        rateLimiter.enforce(RateLimitBucket.PASSWORD_RESET, "email", email.toLowerCase(java.util.Locale.ROOT),
                rateLimiter.config().getPasswordResetEmail(), 1, "forgot-password");
        userRepository.lockByEmail(email).ifPresent(user -> {
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            Instant now = Instant.now();
            user.setResetCode(code);
            user.setResetAttempts(0);
            user.setResetExpiresAt(now.plus(RESET_TTL));
            user.setResetSentAt(now);
            userRepository.save(user);
            emailSender.sendPasswordResetCode(user.getEmail(), code);
        });
    }

    /** Complete a password reset with the emailed code and a new password. */
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.lockByEmail(request.email().trim())
                .orElseThrow(() -> ApiException.validation(MessageKeys.AUTH_CODE_INCORRECT));
        String code = user.getResetCode();
        Instant expiresAt = user.getResetExpiresAt();
        if (code == null || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw ApiException.validation(MessageKeys.AUTH_CODE_EXPIRED);
        }
        if (!code.equals(request.code().trim())) {
            int attempts = user.getResetAttempts() + 1;
            user.setResetAttempts(attempts);
            if (attempts >= 5) {
                user.setResetCode(null);
                user.setResetExpiresAt(null);
            }
            userRepository.save(user);
            throw ApiException.validation(MessageKeys.AUTH_CODE_INCORRECT);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        sessions.revokeAll(user.getId());
        user.setResetCode(null);
        user.setResetExpiresAt(null);
        userRepository.save(user);
    }

    private static String safeName(String name) {
        return (name == null || name.isBlank()) ? null : name.trim();
    }
}
