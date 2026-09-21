package ai.closette.auth.service;

import ai.closette.config.PrivacyProperties;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class AiConsentService {
    private final UserRepository users;
    private final PrivacyProperties privacy;
    public AiConsentService(UserRepository users, PrivacyProperties privacy) {
        this.users = users; this.privacy = privacy;
    }
    public void require(UUID userId) {
        if ("mock".equals(privacy.getAiProvider())) return;
        var user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized(MessageKeys.AUTH_REQUIRED));
        if (!privacy.consentVersion().equals(user.getAiConsentVersion())) {
            throw ApiException.forbidden(MessageKeys.AI_CONSENT_REQUIRED);
        }
    }
    @Transactional
    public void update(UUID userId, String version, boolean accepted) {
        var user = users.lockById(userId).orElseThrow(() -> ApiException.unauthorized(MessageKeys.AUTH_REQUIRED));
        if (accepted && !privacy.consentVersion().equals(version)) {
            throw ApiException.validation(MessageKeys.AI_CONSENT_REQUIRED);
        }
        user.setAiConsentVersion(accepted ? version : null);
        user.setAiConsentAt(accepted ? Instant.now() : null);
    }
}
