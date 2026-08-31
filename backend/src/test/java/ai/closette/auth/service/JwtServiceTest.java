package ai.closette.auth.service;

import ai.closette.config.ClosetteProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token issuing/parsing in isolation — no Spring context. These are the checks
 * that stand between a forged token and someone else's wardrobe.
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-long-enough-for-hs256-0123456789-abcdefgh";

    private static JwtService jwtService(String secret, long accessTtlMinutes, long refreshTtlDays) {
        ClosetteProperties props = new ClosetteProperties();
        props.getJwt().setSecret(secret);
        props.getJwt().setAccessTtlMinutes(accessTtlMinutes);
        props.getJwt().setRefreshTtlDays(refreshTtlDays);
        return new JwtService(props);
    }

    private static JwtService jwtService() {
        return jwtService(SECRET, 60, 30);
    }

    @Test
    void accessTokenCarriesTheUserId() {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();

        assertThat(service.parseAccessToken(service.generateAccessToken(userId))).isEqualTo(userId);
    }

    @Test
    void refreshTokenCarriesTheUserId() {
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();

        assertThat(service.parseRefreshToken(service.generateRefreshToken(userId))).isEqualTo(userId);
    }

    @Test
    void tokenTypesAreNotInterchangeable() {
        // A long-lived refresh token must never be usable as an access token.
        JwtService service = jwtService();
        UUID userId = UUID.randomUUID();

        assertThat(service.parseAccessToken(service.generateRefreshToken(userId))).isNull();
        assertThat(service.parseRefreshToken(service.generateAccessToken(userId))).isNull();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = jwtService("a-different-secret-that-is-also-long-enough-0123456789", 60, 30)
                .generateAccessToken(UUID.randomUUID());

        assertThat(jwtService().parseAccessToken(forged)).isNull();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiring = jwtService(SECRET, -1, -1);
        UUID userId = UUID.randomUUID();

        assertThat(expiring.parseAccessToken(expiring.generateAccessToken(userId))).isNull();
        assertThat(expiring.parseRefreshToken(expiring.generateRefreshToken(userId))).isNull();
    }

    @Test
    void theShippedPlaceholderSecretIsRefused() {
        // The default in application.yml is public. Booting with it would accept
        // tokens minted by anyone who has read the repository.
        assertThatThrownBy(() -> jwtService("change-me-to-a-long-random-secret-at-least-256-bits-long-000000", 60, 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void aSecretTooShortForHs256IsRefused() {
        assertThatThrownBy(() -> jwtService("short", 60, 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void malformedTokenIsRejectedInsteadOfThrowing() {
        // The JWT filter treats null as "not authenticated"; an exception here
        // would surface as a 500 on every request with a junk header.
        JwtService service = jwtService();

        assertThat(service.parseAccessToken("not-a-token")).isNull();
        assertThat(service.parseAccessToken("")).isNull();
        assertThat(service.parseAccessToken(null)).isNull();
    }
}
