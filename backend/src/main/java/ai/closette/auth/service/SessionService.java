package ai.closette.auth.service;

import ai.closette.auth.dto.AuthResponse;
import ai.closette.auth.model.AuthSession;
import ai.closette.auth.repository.AuthSessionRepository;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.config.ClosetteProperties;
import ai.closette.user.dto.UserResponse;
import ai.closette.user.model.User;
import ai.closette.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class SessionService {
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final JwtService jwt;
    private final ClosetteProperties props;

    public SessionService(AuthSessionRepository sessions, UserRepository users,
                          JwtService jwt, ClosetteProperties props) {
        this.sessions = sessions;
        this.users = users;
        this.jwt = jwt;
        this.props = props;
    }

    @Transactional
    public AuthResponse issue(User user) {
        return tokens(user, new AuthSession(user.getId(),
                Instant.now().plus(props.getJwt().getRefreshTtlDays(), ChronoUnit.DAYS)));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse rotate(String token) {
        var claims = jwt.parseSession(token, "refresh");
        if (claims == null) throw invalid();
        // The user lock serializes refresh, password reset, logout and account deletion.
        User user = users.lockById(claims.userId()).orElseThrow(SessionService::invalid);
        AuthSession session = sessions.findById(claims.sessionId()).orElseThrow(SessionService::invalid);
        if (!session.getUserId().equals(user.getId()) || !session.getExpiresAt().isAfter(Instant.now())) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(session.getRefreshHash().getBytes(StandardCharsets.UTF_8),
                hash(token).getBytes(StandardCharsets.UTF_8))) {
            // Reuse of an already rotated token invalidates the entire session.
            sessions.delete(session);
            throw invalid();
        }
        return tokens(user, session);
    }

    public UUID authenticate(String token) {
        var claims = jwt.parseSession(token, "access");
        return claims != null && sessions.existsByIdAndUserIdAndExpiresAtAfter(
                claims.sessionId(), claims.userId(), Instant.now()) ? claims.userId() : null;
    }

    @Transactional
    public void logout(String token) {
        var claims = jwt.parseSession(token, "refresh");
        if (claims == null || users.lockById(claims.userId()).isEmpty()) return;
        sessions.findById(claims.sessionId()).filter(s -> s.getUserId().equals(claims.userId()))
                .ifPresent(sessions::delete);
    }

    @Transactional
    public void revokeAll(UUID userId) { sessions.deleteByUserId(userId); }

    private AuthResponse tokens(User user, AuthSession session) {
        String refresh = jwt.generateRefreshToken(user.getId(), session.getId());
        session.setRefreshHash(hash(refresh));
        sessions.save(session);
        return new AuthResponse(jwt.generateAccessToken(user.getId(), session.getId()),
                refresh, UserResponse.from(user));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ApiException invalid() {
        return ApiException.unauthorized(MessageKeys.AUTH_INVALID_REFRESH_TOKEN);
    }
}
