package ai.closette.auth.service;

import ai.closette.config.ClosetteProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates stateless JWT access/refresh tokens.
 */
@Service
public class JwtService {

    private static final String TYPE_CLAIM = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    /** The placeholder shipped in application.yml and .env.example. */
    private static final String PLACEHOLDER_SECRET = "change-me";
    private static final int MIN_SECRET_BYTES = 32;

    public JwtService(ClosetteProperties props) {
        String secret = props.getJwt().getSecret();
        requireRealSecret(secret);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.getJwt().getAccessTtlMinutes());
        this.refreshTtl = Duration.ofDays(props.getJwt().getRefreshTtlDays());
    }

    /**
     * Refuses to start on the shipped placeholder.
     *
     * The default in application.yml exists so the app runs locally, but it is
     * public: anything deployed with it would accept tokens minted by anyone who
     * has read the repository. Failing at startup is the only way that mistake
     * gets noticed.
     */
    private static void requireRealSecret(String secret) {
        if (secret == null || secret.isBlank() || secret.startsWith(PLACEHOLDER_SECRET)) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the placeholder from application.yml. Set a real one "
                            + "(at least " + MIN_SECRET_BYTES + " random bytes) before starting.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short for HS256: needs at least " + MIN_SECRET_BYTES + " bytes.");
        }
    }

    public String generateAccessToken(UUID userId) {
        return build(userId, TYPE_ACCESS, accessTtl);
    }

    public String generateRefreshToken(UUID userId) {
        return build(userId, TYPE_REFRESH, refreshTtl);
    }

    private String build(UUID userId, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(TYPE_CLAIM, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Returns the user id for a valid access token, or null if invalid/expired. */
    public UUID parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            if (!TYPE_ACCESS.equals(claims.get(TYPE_CLAIM, String.class))) {
                return null;
            }
            return UUID.fromString(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns the user id for a valid refresh token, or null if invalid/expired. */
    public UUID parseRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            if (!TYPE_REFRESH.equals(claims.get(TYPE_CLAIM, String.class))) {
                return null;
            }
            return UUID.fromString(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}
