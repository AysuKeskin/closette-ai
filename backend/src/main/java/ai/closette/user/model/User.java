package ai.closette.user.model;

import ai.closette.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import ai.closette.usage.model.UserPlan;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "username", nullable = false, unique = true, length = 30)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /** What this account is entitled to. Billing sets it; until then everyone is FREE. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserPlan plan = UserPlan.FREE;

    @Column(name = "verification_code", length = 10)
    private String verificationCode;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

    @Column(name = "verification_sent_at")
    private Instant verificationSentAt;

    /** Wrong codes entered against the current verification code. */
    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts = 0;

    @Column(name = "reset_code", length = 10)
    private String resetCode;

    @Column(name = "reset_expires_at")
    private Instant resetExpiresAt;

    @Column(name = "reset_sent_at")
    private Instant resetSentAt;

    @Column(name = "reset_attempts", nullable = false)
    private int resetAttempts;

    public int getResetAttempts() { return resetAttempts; }
    public void setResetAttempts(int value) { resetAttempts = value; }

    @Column(length = 64) private String aiConsentVersion;
    private Instant aiConsentAt;
    public String getAiConsentVersion() { return aiConsentVersion; }
    public void setAiConsentVersion(String v) { aiConsentVersion = v; }
    public Instant getAiConsentAt() { return aiConsentAt; }
    public void setAiConsentAt(Instant v) { aiConsentAt = v; }

    protected User() {
    }

    public User(String email, String username, String passwordHash, String displayName) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public Instant getVerificationExpiresAt() {
        return verificationExpiresAt;
    }

    public void setVerificationExpiresAt(Instant verificationExpiresAt) {
        this.verificationExpiresAt = verificationExpiresAt;
    }

    public Instant getVerificationSentAt() {
        return verificationSentAt;
    }

    public void setVerificationSentAt(Instant verificationSentAt) {
        this.verificationSentAt = verificationSentAt;
    }

    public int getVerificationAttempts() {
        return verificationAttempts;
    }

    public void setVerificationAttempts(int verificationAttempts) {
        this.verificationAttempts = verificationAttempts;
    }

    public String getResetCode() {
        return resetCode;
    }

    public void setResetCode(String resetCode) {
        this.resetCode = resetCode;
    }

    public Instant getResetExpiresAt() {
        return resetExpiresAt;
    }

    public void setResetExpiresAt(Instant resetExpiresAt) {
        this.resetExpiresAt = resetExpiresAt;
    }

    public Instant getResetSentAt() {
        return resetSentAt;
    }

    public void setResetSentAt(Instant resetSentAt) {
        this.resetSentAt = resetSentAt;
    }

    public UserPlan getPlan() {
        return plan;
    }

    public void setPlan(UserPlan plan) {
        this.plan = plan;
    }
}
