package ai.closette.auth.service;

import ai.closette.auth.dto.AuthResponse;
import ai.closette.auth.dto.ForgotPasswordRequest;
import ai.closette.auth.dto.LoginRequest;
import ai.closette.auth.dto.RefreshRequest;
import ai.closette.auth.dto.RegisterRequest;
import ai.closette.auth.dto.ResetPasswordRequest;
import ai.closette.common.exception.ApiException;
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
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final EmailSender emailSender;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailVerificationService emailVerificationService,
                       EmailSender emailSender) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
        this.emailSender = emailSender;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String username = request.username().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("email: An account with this email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("username: This username is already taken");
        }
        User user = new User(
                email,
                username,
                passwordEncoder.encode(request.password()),
                safeName(request.displayName()));
        user = userRepository.save(user);
        // Soft verification: account is usable now; email a code to verify later.
        emailVerificationService.issue(user);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest request) {
        UUID userId = jwtService.parseRefreshToken(request.refreshToken());
        if (userId == null) {
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        return issueTokens(user);
    }

    /**
     * Start a password reset. Always succeeds from the caller's view (we never
     * reveal whether an email is registered); a code is only sent if it exists.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim();
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            Instant now = Instant.now();
            user.setResetCode(code);
            user.setResetExpiresAt(now.plus(RESET_TTL));
            user.setResetSentAt(now);
            userRepository.save(user);
            emailSender.sendPasswordResetCode(user.getEmail(), code);
        });
    }

    /** Complete a password reset with the emailed code and a new password. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> validation("code: That code is incorrect"));
        String code = user.getResetCode();
        Instant expiresAt = user.getResetExpiresAt();
        if (code == null || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw validation("code: This code has expired — request a new one");
        }
        if (!code.equals(request.code().trim())) {
            throw validation("code: That code is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setResetCode(null);
        user.setResetExpiresAt(null);
        userRepository.save(user);
    }

    private static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, message);
    }

    private AuthResponse issueTokens(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user.getId()),
                jwtService.generateRefreshToken(user.getId()),
                UserResponse.from(user));
    }

    private static String safeName(String name) {
        return (name == null || name.isBlank()) ? null : name.trim();
    }
}
