package ai.closette.email;

/**
 * Provider-independent email seam (mirrors the AI-service approach): the app
 * depends only on this interface, never on a concrete provider. Ships a no-keys
 * dev implementation that logs the code; a real SMTP/SES/Resend sender can be
 * swapped in later via configuration with no changes to callers.
 */
public interface EmailSender {

    void sendVerificationCode(String toEmail, String code);

    void sendPasswordResetCode(String toEmail, String code);
}
