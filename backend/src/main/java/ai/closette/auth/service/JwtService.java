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

    public JwtService(ClosetteProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.getJwt().getAccessTtlMinutes());
        this.refreshTtl = Duration.ofDays(props.getJwt().getRefreshTtlDays());
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
