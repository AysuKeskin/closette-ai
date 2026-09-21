package ai.closette.auth.service;

import ai.closette.auth.dto.*;
import ai.closette.common.exception.ApiException;
import ai.closette.support.TestData;
import ai.closette.user.repository.UserRepository;
import ai.closette.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SessionSecurityTest {
    @Autowired AuthService auth;
    @Autowired SessionService sessions;
    @Autowired UserRepository users;
    @Autowired UserService userService;

    @Test
    void aRotatedRefreshTokenCannotBeReplayedAndRevokesItsSession() {
        var initial = TestData.register(auth);
        var rotated = auth.refresh(new RefreshRequest(initial.refreshToken()));
        assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());
        assertThat(sessions.authenticate(rotated.accessToken())).isEqualTo(initial.user().id());
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest(initial.refreshToken())))
                .isInstanceOf(ApiException.class);
        assertThat(sessions.authenticate(rotated.accessToken())).isNull();
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest(rotated.refreshToken())))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void logoutRevokesBothTokensButLeavesAnotherDeviceSignedIn() {
        var first = TestData.register(auth);
        var other = auth.login(new LoginRequest(first.user().email(), "Password123"));
        auth.logout(new RefreshRequest(first.refreshToken()));
        assertThat(sessions.authenticate(first.accessToken())).isNull();
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest(first.refreshToken())))
                .isInstanceOf(ApiException.class);
        assertThat(sessions.authenticate(other.accessToken())).isEqualTo(first.user().id());
    }

    @Test
    void passwordResetRevokesEveryDeviceImmediately() {
        var first = TestData.register(auth);
        var other = auth.login(new LoginRequest(first.user().email(), "Password123"));
        auth.forgotPassword(new ForgotPasswordRequest(first.user().email()));
        String code = users.findById(first.user().id()).orElseThrow().getResetCode();
        auth.resetPassword(new ResetPasswordRequest(first.user().email(), code, "UpdatedPass123"));
        assertThat(sessions.authenticate(first.accessToken())).isNull();
        assertThat(sessions.authenticate(other.accessToken())).isNull();
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest(other.refreshToken())))
                .isInstanceOf(ApiException.class);
        assertThat(auth.login(new LoginRequest(first.user().email(), "UpdatedPass123"))).isNotNull();
    }

    @Test
    void accountDeletionRevokesTheSession() {
        var session = TestData.register(auth);
        userService.deleteAccount(session.user().id());
        assertThat(sessions.authenticate(session.accessToken())).isNull();
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest(session.refreshToken())))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fiveWrongCodesBurnTheCodeDespiteTransactionalErrors() {
        var session = TestData.register(auth);
        auth.forgotPassword(new ForgotPasswordRequest(session.user().email()));
        String code = users.findById(session.user().id()).orElseThrow().getResetCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest(
                    session.user().email(), wrong, "UpdatedPass123"))).isInstanceOf(ApiException.class);
        }
        var user = users.findById(session.user().id()).orElseThrow();
        assertThat(user.getResetAttempts()).isEqualTo(5);
        assertThat(user.getResetCode()).isNull();
        assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest(
                user.getEmail(), code, "UpdatedPass123"))).isInstanceOf(ApiException.class);
        assertThat(auth.login(new LoginRequest(user.getEmail(), "Password123"))).isNotNull();
    }

    @Test
    void parallelWrongCodesCannotLoseAttempts() throws Exception {
        var session = TestData.register(auth);
        auth.forgotPassword(new ForgotPasswordRequest(session.user().email()));
        String code = users.findById(session.user().id()).orElseThrow().getResetCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        try (var executor = Executors.newFixedThreadPool(5)) {
            var ready = new CountDownLatch(5);
            var go = new CountDownLatch(1);
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 5; i++) tasks.add(executor.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest(
                        session.user().email(), wrong, "UpdatedPass123"))).isInstanceOf(ApiException.class);
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertThat(users.findById(session.user().id()).orElseThrow().getResetCode()).isNull();
    }
}
