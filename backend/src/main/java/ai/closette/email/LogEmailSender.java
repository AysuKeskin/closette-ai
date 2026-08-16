package ai.closette.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default dev/mock email sender (no API keys). Instead of sending mail it logs
 * the verification code so the whole flow works offline. Active unless another
 * provider is selected via {@code closette.email.provider}.
 */
@Component
@ConditionalOnProperty(name = "closette.email.provider", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        log.info("""

                ┌───────────────────────────────────────────────┐
                │  Closette — email verification (DEV/log mode)  │
                │  To:   {}
                │  Code: {}
                │  (No real email sent; wire a provider for prod) │
                └───────────────────────────────────────────────┘
                """, toEmail, code);
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        log.info("""

                ┌───────────────────────────────────────────────┐
                │  Closette — password reset (DEV/log mode)      │
                │  To:   {}
                │  Code: {}
                │  (No real email sent; wire a provider for prod) │
                └───────────────────────────────────────────────┘
                """, toEmail, code);
    }
}
