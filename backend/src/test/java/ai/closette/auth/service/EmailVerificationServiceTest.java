package ai.closette.auth.service;

import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.support.TestData;
import ai.closette.user.dto.UserResponse;
import ai.closette.user.model.User;
import ai.closette.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Soft email verification: code issuing, checking, expiry and resend cooldown. */
@SpringBootTest
@ActiveProfiles("test")
class EmailVerificationServiceTest {

    @Autowired
    AuthService authService;

    @Autowired
    EmailVerificationService verificationService;

    @Autowired
    UserRepository userRepository;

    private UUID newUser() {
        return TestData.newUser(authService);
    }

    private User reload(UUID userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    @Test
    void registrationIssuesASixDigitCodeThatExpires() {
        UUID userId = newUser();

        User user = reload(userId);
        assertThat(user.getVerificationCode()).matches("\\d{6}");
        assertThat(user.getVerificationExpiresAt()).isAfter(Instant.now());
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void correctCodeVerifiesTheAccountAndClearsTheCode() {
        UUID userId = newUser();
        String code = reload(userId).getVerificationCode();

        UserResponse response = verificationService.verify(userId, code);

        assertThat(response.emailVerified()).isTrue();
        // The code is single-use; leaving it on the row would keep it replayable.
        assertThat(reload(userId).getVerificationCode()).isNull();
    }

    @Test
    void surroundingWhitespaceInTheCodeIsForgiven() {
        UUID userId = newUser();
        String code = reload(userId).getVerificationCode();

        assertThat(verificationService.verify(userId, "  " + code + " ").emailVerified()).isTrue();
    }

    @Test
    void verifyingAnAlreadyVerifiedAccountIsANoOp() {
        UUID userId = newUser();
        verificationService.verify(userId, reload(userId).getVerificationCode());

        // Idempotent: a double-tap on "verify" must not fail the user.
        assertThat(verificationService.verify(userId, "000000").emailVerified()).isTrue();
    }

    @Test
    void wrongCodeIsRejected() {
        UUID userId = newUser();
        String code = reload(userId).getVerificationCode();
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> verificationService.verify(userId, wrongCode))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(ex.getMessage()).startsWith("code:");
                });
        assertThat(reload(userId).isEmailVerified()).isFalse();
    }

    @Test
    void expiredCodeIsRejectedWithAResendHint() {
        UUID userId = newUser();
        User user = reload(userId);
        user.setVerificationExpiresAt(Instant.now().minusSeconds(1));
        userRepository.save(user);

        assertThatThrownBy(() -> verificationService.verify(userId, user.getVerificationCode()))
                .hasMessageContaining("expired");
    }

    @Test
    void resendIsBlockedDuringTheCooldown() {
        UUID userId = newUser(); // registration just sent a code

        assertThatThrownBy(() -> verificationService.resend(userId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RATE_LIMITED));
    }

    @Test
    void resendAfterTheCooldownReplacesTheCode() {
        UUID userId = newUser();
        User user = reload(userId);
        Instant staleSentAt = Instant.now().minusSeconds(120);
        user.setVerificationSentAt(staleSentAt);
        userRepository.save(user);

        verificationService.resend(userId);

        User refreshed = reload(userId);
        assertThat(refreshed.getVerificationCode()).matches("\\d{6}");
        assertThat(refreshed.getVerificationExpiresAt()).isAfter(Instant.now());
        // A fresh send restarts the cooldown for the next resend.
        assertThat(refreshed.getVerificationSentAt()).isAfter(staleSentAt);
    }

    @Test
    void resendOnAVerifiedAccountIsAConflict() {
        UUID userId = newUser();
        verificationService.verify(userId, reload(userId).getVerificationCode());

        assertThatThrownBy(() -> verificationService.resend(userId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void verifyingAnUnknownUserIsNotFound() {
        assertThatThrownBy(() -> verificationService.verify(UUID.randomUUID(), "123456"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
