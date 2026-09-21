package ai.closette.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionGuardTest {
    private ClosetteProperties properties() {
        var p = new ClosetteProperties();
        p.getStorage().setPublicEndpoint("https://photos.closette.app");
        p.getStorage().setSecretKey("a-production-storage-secret-123456789");
        return p;
    }
    private PrivacyProperties privacy() {
        var p = new PrivacyProperties();
        p.setOperator("Example legal operator");
        p.setSupportEmail("help@closette.app");
        p.setPublicBaseUrl("https://api.closette.app");
        p.setAiProvider("openai");
        p.setAiPrivacyUrl("https://provider.closette.app/privacy");
        p.setAiProcessingDetails("Configured processing and retention");
        p.setInfrastructureDetails("Configured hosting and email regions");
        return p;
    }
    private MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("closette.email.provider", "resend")
                .withProperty("closette.email.resend-api-key", "test-key")
                .withProperty("closette.email.from", "Closette <hello@closette.app>")
                .withProperty("spring.datasource.password", "a-production-database-secret-123456")
                .withProperty("spring.data.redis.password", "a-production-redis-secret-123456789")
                .withProperty("springdoc.api-docs.enabled", "false");
    }
    @Test
    void completeConfigurationStarts() {
        assertThatCode(() -> new ProductionGuard(properties(), privacy(), environment())).doesNotThrowAnyException();
    }
    @Test
    void loggingOnlyQuotasCannotShip() {
        var p = properties(); p.getRatelimit().setMode(ClosetteProperties.RateLimits.Mode.LOG);
        assertThatThrownBy(() -> new ProductionGuard(p, privacy(), environment())).hasMessageContaining("rate limits");
    }
    @Test
    void aDevelopmentEmailSenderCannotShip() {
        assertThatThrownBy(() -> new ProductionGuard(properties(), privacy(),
                environment().withProperty("closette.email.provider", "log"))).hasMessageContaining("email provider");
    }
    @Test
    void publicHttpStorageAndDefaultPasswordsCannotShip() {
        var p = properties(); p.getStorage().setPublicEndpoint("http://localhost:9000");
        assertThatThrownBy(() -> new ProductionGuard(p, privacy(), environment())).hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new ProductionGuard(properties(), privacy(),
                environment().withProperty("spring.datasource.password", "closette"))).hasMessageContaining("POSTGRES_PASSWORD");
    }
    @Test
    void missingProviderDisclosureCannotShip() {
        var p = privacy(); p.setAiProcessingDetails("");
        assertThatThrownBy(() -> new ProductionGuard(properties(), p, environment())).hasMessageContaining("AI_PROCESSING_DETAILS");
    }
}
