package ai.closette.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;

/** Refuse to boot a public deployment with development credentials or disclosures. */
@Component
@Profile("prod")
public class ProductionGuard {
    public ProductionGuard(ClosetteProperties props, PrivacyProperties privacy, Environment env) {
        if (props.getRatelimit().getMode() != ClosetteProperties.RateLimits.Mode.ENFORCE)
            throw new IllegalStateException("Production requires enforced rate limits");
        https("PUBLIC_BASE_URL", privacy.getPublicBaseUrl());
        https("STORAGE_PUBLIC_ENDPOINT", props.getStorage().getPublicEndpoint());
        https("AI_PRIVACY_URL", privacy.getAiPrivacyUrl());
        required("PRIVACY_OPERATOR", privacy.getOperator());
        required("SUPPORT_EMAIL", privacy.getSupportEmail());
        if (!privacy.getSupportEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalStateException("Set a working SUPPORT_EMAIL");
        required("AI_PROVIDER", privacy.getAiProvider());
        required("AI_PROCESSING_DETAILS", privacy.getAiProcessingDetails());
        required("INFRASTRUCTURE_DETAILS", privacy.getInfrastructureDetails());
        if (privacy.getBackupRetentionDays() < 1 || privacy.getBackupRetentionDays() > 365)
            throw new IllegalStateException("Set BACKUP_RETENTION_DAYS to your actual backup lifecycle");
        if (!"resend".equals(env.getProperty("closette.email.provider")))
            throw new IllegalStateException("Production requires a real email provider");
        required("RESEND_API_KEY", env.getProperty("closette.email.resend-api-key"));
        String sender = env.getProperty("closette.email.from", "");
        if (sender.isBlank() || sender.contains("resend.dev"))
            throw new IllegalStateException("EMAIL_FROM must use your verified sending domain");
        secret("POSTGRES_PASSWORD", env.getProperty("spring.datasource.password"));
        secret("REDIS_PASSWORD", env.getProperty("spring.data.redis.password"));
        secret("MINIO_ROOT_PASSWORD", props.getStorage().getSecretKey());
        if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, true))
            throw new IllegalStateException("Disable public API docs in production");
    }
    private static void required(String name, String value) {
        if (value == null || value.isBlank() || value.contains("development") || value.contains("example.")
                || value.equals("mock") || value.startsWith("change-me"))
            throw new IllegalStateException("Configure " + name + " before production startup");
    }
    private static void secret(String name, String value) {
        required(name, value);
        if (value.length() < 24 || value.startsWith("closette"))
            throw new IllegalStateException(name + " must be a strong production secret (24+ characters)");
    }
    private static void https(String name, String value) {
        required(name, value);
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getHost().equals("localhost"))
            throw new IllegalStateException(name + " must use a public HTTPS origin");
    }
}
