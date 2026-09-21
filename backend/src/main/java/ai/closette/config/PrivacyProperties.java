package ai.closette.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@ConfigurationProperties(prefix = "closette.privacy")
public class PrivacyProperties {
    private String operator = "Closette (development)";
    private String supportEmail = "";
    private String publicBaseUrl = "http://localhost:8080";
    private String aiProvider = "mock";
    private String aiPrivacyUrl = "";
    private String aiProcessingDetails = "Local development mock; no external AI transfer.";
    private String infrastructureDetails = "Local development storage.";
    private int backupRetentionDays = 30;

    public String getOperator() { return operator; }
    public void setOperator(String v) { operator = v; }
    public String getSupportEmail() { return supportEmail; }
    public void setSupportEmail(String v) { supportEmail = v; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String v) { publicBaseUrl = v; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String v) { aiProvider = v; }
    public String getAiPrivacyUrl() { return aiPrivacyUrl; }
    public void setAiPrivacyUrl(String v) { aiPrivacyUrl = v; }
    public String getAiProcessingDetails() { return aiProcessingDetails; }
    public void setAiProcessingDetails(String v) { aiProcessingDetails = v; }
    public String getInfrastructureDetails() { return infrastructureDetails; }
    public void setInfrastructureDetails(String v) { infrastructureDetails = v; }
    public int getBackupRetentionDays() { return backupRetentionDays; }
    public void setBackupRetentionDays(int v) { backupRetentionDays = v; }
    public String consentVersion() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    ("2026-09-21|" + aiProvider + "|" + aiPrivacyUrl + "|" + aiProcessingDetails)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
