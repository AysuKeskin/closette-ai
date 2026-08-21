package ai.closette.auth.service;

import ai.closette.auth.dto.AuthResponse;
import ai.closette.auth.dto.ForgotPasswordRequest;
import ai.closette.auth.dto.LoginRequest;
import ai.closette.auth.dto.RefreshRequest;
import ai.closette.auth.dto.RegisterRequest;
import ai.closette.auth.dto.ResetPasswordRequest;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.user.model.User;
import ai.closette.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Registration, login, refresh and password reset against the H2 database. */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    @Autowired
    PasswordEncoder passwordEncoder;

    private RegisterRequest registration(String email, String username) {
        return new RegisterRequest(email, username, "Password123", "Test User");
    }

    private String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@test.io";
    }

    private String uniqueUsername() {
        return "auth_" + System.nanoTime();
    }

    @Test
    void registrationIssuesUsableTokensForTheNewUser() {
        AuthResponse response = authService.register(registration(uniqueEmail(), uniqueUsername()));

        UUID userId = response.user().id();
        assertThat(jwtService.parseAccessToken(response.accessToken())).isEqualTo(userId);
        assertThat(jwtService.parseRefreshToken(response.refreshToken())).isEqualTo(userId);
        // Soft verification: the account works immediately, unverified.
        assertThat(response.user().emailVerified()).isFalse();
    }

    @Test
    void registrationStoresTheEmailLowercasedAndHashesThePassword() {
        String email = uniqueEmail().toUpperCase();

        UUID userId = authService.register(registration(email, uniqueUsername())).user().id();

        User stored = userRepository.findById(userId).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo(email.toLowerCase());
        assertThat(stored.getPasswordHash()).isNotEqualTo("Password123");
        assertThat(passwordEncoder.matches("Password123", stored.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailIsRejectedWithAFieldPrefixedMessage() {
        String email = uniqueEmail();
        authService.register(registration(email, uniqueUsername()));

        assertThatThrownBy(() -> authService.register(registration(email.toUpperCase(), uniqueUsername())))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT);
                    // Prefix is what puts the message under the email input.
                    assertThat(ex.getMessage()).startsWith("email:");
                });
    }

    @Test
    void duplicateUsernameIsRejectedRegardlessOfCase() {
        String username = uniqueUsername();
        authService.register(registration(uniqueEmail(), username));

        assertThatThrownBy(() -> authService.register(registration(uniqueEmail(), username.toUpperCase())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).startsWith("username:"));
    }

    @Test
    void loginAcceptsTheEmailInAnyCase() {
        String email = uniqueEmail();
        UUID userId = authService.register(registration(email, uniqueUsername())).user().id();

        AuthResponse response = authService.login(new LoginRequest(email.toUpperCase(), "Password123"));

        assertThat(response.user().id()).isEqualTo(userId);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() {
        String email = uniqueEmail();
        authService.register(registration(email, uniqueUsername()));

        // Different messages here would let an attacker enumerate accounts.
        Throwable wrongPassword = catchApiException(() -> authService.login(new LoginRequest(email, "Wrong12345")));
        Throwable unknownEmail = catchApiException(() -> authService.login(new LoginRequest(uniqueEmail(), "Password123")));

        assertThat(wrongPassword).hasMessage("Invalid email or password");
        assertThat(unknownEmail).hasMessage(wrongPassword.getMessage());
    }

    @Test
    void refreshIssuesTokensForTheSameUser() {
        AuthResponse registered = authService.register(registration(uniqueEmail(), uniqueUsername()));

        AuthResponse refreshed = authService.refresh(new RefreshRequest(registered.refreshToken()));

        assertThat(refreshed.user().id()).isEqualTo(registered.user().id());
        assertThat(jwtService.parseAccessToken(refreshed.accessToken())).isEqualTo(registered.user().id());
    }

    @Test
    void refreshRejectsAnAccessTokenOrGarbage() {
        AuthResponse registered = authService.register(registration(uniqueEmail(), uniqueUsername()));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(registered.accessToken())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("not-a-token")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void forgotPasswordStaysSilentForAnUnknownEmail() {
        // Succeeding either way is deliberate: it hides whether the email exists.
        assertThatCode(() -> authService.forgotPassword(new ForgotPasswordRequest(uniqueEmail())))
                .doesNotThrowAnyException();
    }

    @Test
    void forgotPasswordStoresASixDigitCodeWithAnExpiry() {
        String email = uniqueEmail();
        UUID userId = authService.register(registration(email, uniqueUsername())).user().id();

        authService.forgotPassword(new ForgotPasswordRequest(email));

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getResetCode()).matches("\\d{6}");
        assertThat(user.getResetExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void resetPasswordSwapsThePasswordAndConsumesTheCode() {
        String email = uniqueEmail();
        UUID userId = authService.register(registration(email, uniqueUsername())).user().id();
        authService.forgotPassword(new ForgotPasswordRequest(email));
        String code = userRepository.findById(userId).orElseThrow().getResetCode();

        authService.resetPassword(new ResetPasswordRequest(email, code, "NewPassword1"));

        assertThat(authService.login(new LoginRequest(email, "NewPassword1")).user().id()).isEqualTo(userId);
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "Password123")))
                .isInstanceOf(ApiException.class);
        // A used code must not work twice.
        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(email, code, "Another1234")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void resetPasswordRejectsTheWrongCode() {
        String email = uniqueEmail();
        UUID userId = authService.register(registration(email, uniqueUsername())).user().id();
        authService.forgotPassword(new ForgotPasswordRequest(email));
        String code = userRepository.findById(userId).orElseThrow().getResetCode();
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(email, wrongCode, "NewPassword1")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(ex.getMessage()).startsWith("code:");
                });
    }

    @Test
    void resetPasswordRejectsAnExpiredCode() {
        String email = uniqueEmail();
        UUID userId = authService.register(registration(email, uniqueUsername())).user().id();
        authService.forgotPassword(new ForgotPasswordRequest(email));
        User user = userRepository.findById(userId).orElseThrow();
        user.setResetExpiresAt(Instant.now().minusSeconds(1));
        userRepository.save(user);

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordRequest(email, user.getResetCode(), "NewPassword1")))
                .hasMessageContaining("expired");
    }

    @Test
    void resetPasswordForAnUnknownEmailLooksLikeAWrongCode() {
        // Same failure as a bad code, so the response can't confirm the address.
        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordRequest(uniqueEmail(), "123456", "NewPassword1")))
                .hasMessage("code: That code is incorrect");
    }

    private static Throwable catchApiException(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected an ApiException");
        } catch (ApiException e) {
            return e;
        }
    }
}
