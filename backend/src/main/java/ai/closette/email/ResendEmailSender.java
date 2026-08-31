package ai.closette.email;

import ai.closette.common.exception.MessageKeys;
import ai.closette.common.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Real email via Resend (free tier, no domain needed for tests via
 * onboarding@resend.dev). Active when {@code closette.email.provider=resend};
 * falls back to logging the code if the API key is missing or the call fails, so
 * the app never breaks.
 *
 * The mail is sent while the request that triggered it is still in flight, so the
 * copy resolves in the language that request asked for — a Turkish sign-up gets a
 * Turkish email.
 */
@Component
@ConditionalOnProperty(name = "closette.email.provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final String apiKey;
    private final String from;
    private final Messages messages;
    private final WebClient client = WebClient.builder().baseUrl("https://api.resend.com").build();

    public ResendEmailSender(
            @Value("${closette.email.resend-api-key:}") String apiKey,
            @Value("${closette.email.from:Closette <onboarding@resend.dev>}") String from,
            Messages messages) {
        this.apiKey = apiKey;
        this.from = from;
        this.messages = messages;
        warnIfUnusable(apiKey);
    }

    /**
     * Says at startup what would otherwise only show up as a 401 per request,
     * long after someone wondered why no code arrived. The provider was chosen
     * deliberately, so a key that cannot work is a misconfiguration worth naming.
     */
    private static void warnIfUnusable(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("EMAIL_PROVIDER=resend but RESEND_API_KEY is empty — "
                    + "no verification or reset email will be delivered; codes are logged instead");
        } else if (apiKey.contains("REPLACE") || apiKey.contains("your-key")) {
            log.warn("EMAIL_PROVIDER=resend but RESEND_API_KEY is still a placeholder — "
                    + "no email will be delivered; get a key at https://resend.com/api-keys");
        }
    }

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        send(toEmail, messages.get(MessageKeys.EMAIL_VERIFY_SUBJECT),
                paragraph(messages.get(MessageKeys.EMAIL_VERIFY_GREETING))
                        + paragraph(messages.get(MessageKeys.EMAIL_VERIFY_INTRO))
                        + codeBlock(code)
                        + paragraph(messages.get(MessageKeys.EMAIL_EXPIRY)),
                code);
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        send(toEmail, messages.get(MessageKeys.EMAIL_RESET_SUBJECT),
                paragraph(messages.get(MessageKeys.EMAIL_RESET_INTRO))
                        + paragraph(messages.get(MessageKeys.EMAIL_RESET_CODE))
                        + codeBlock(code)
                        + paragraph(messages.get(MessageKeys.EMAIL_EXPIRY) + " "
                                + messages.get(MessageKeys.EMAIL_RESET_IGNORE)),
                code);
    }

    private static String paragraph(String text) {
        return "<p>" + text + "</p>";
    }

    private static String codeBlock(String code) {
        return "<h2 style=\"letter-spacing:4px\">" + code + "</h2>";
    }

    private void send(String to, String subject, String html, String code) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY missing — logging code instead. To {}: {}", to, code);
            return;
        }
        try {
            client.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("from", from, "to", to, "subject", subject, "html", html))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            // Never block signup/reset on an email hiccup — the code is still valid.
            log.warn("Resend send failed for {} — code was {} ({})", to, code, e.getMessage());
        }
    }
}
