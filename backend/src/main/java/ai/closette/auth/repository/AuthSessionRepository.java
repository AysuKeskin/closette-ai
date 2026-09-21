package ai.closette.auth.repository;

import ai.closette.auth.model.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    boolean existsByIdAndUserIdAndExpiresAtAfter(UUID id, UUID userId, Instant now);
    void deleteByUserId(UUID userId);
}
