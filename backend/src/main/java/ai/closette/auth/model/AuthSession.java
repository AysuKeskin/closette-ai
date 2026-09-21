package ai.closette.auth.model;

import ai.closette.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession extends BaseEntity {
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false, length = 64) private String refreshHash;
    @Column(nullable = false) private Instant expiresAt;

    protected AuthSession() { }
    public AuthSession(UUID userId, Instant expiresAt) {
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
    public UUID getUserId() { return userId; }
    public String getRefreshHash() { return refreshHash; }
    public void setRefreshHash(String value) { refreshHash = value; }
    public Instant getExpiresAt() { return expiresAt; }
}
