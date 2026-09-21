package ai.closette.user.controller;

import ai.closette.config.PrivacyProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class PrivacyController {
    private final PrivacyProperties privacy;
    public PrivacyController(PrivacyProperties privacy) { this.privacy = privacy; }

    @GetMapping("/api/privacy")
    public Map<String, Object> disclosure() {
        return Map.of("version", privacy.consentVersion(), "provider", privacy.getAiProvider(),
                "providerPrivacyUrl", privacy.getAiPrivacyUrl(), "processingDetails", privacy.getAiProcessingDetails(),
                "requiresConsent", !"mock".equals(privacy.getAiProvider()),
                "privacyUrl", privacy.getPublicBaseUrl() + "/privacy",
                "supportUrl", privacy.getPublicBaseUrl() + "/support");
    }

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public String policy(@RequestParam(defaultValue = "en") String lang) throws IOException {
        return page("privacy", lang);
    }

    @GetMapping(value = "/support", produces = MediaType.TEXT_HTML_VALUE)
    public String support(@RequestParam(defaultValue = "en") String lang) throws IOException {
        return page("support", lang);
    }

    private String page(String name, String lang) throws IOException {
        String language = "tr".equals(lang) ? "tr" : "en";
        String template = new ClassPathResource("legal/" + name + "-" + language + ".html")
                .getContentAsString(StandardCharsets.UTF_8);
        Map<String, String> values = Map.of("operator", privacy.getOperator(), "email", privacy.getSupportEmail(),
                "provider", privacy.getAiProvider(), "providerUrl", privacy.getAiPrivacyUrl(),
                "processing", privacy.getAiProcessingDetails(), "infrastructure", privacy.getInfrastructureDetails(),
                "backupDays", String.valueOf(privacy.getBackupRetentionDays()));
        for (var entry : values.entrySet()) template = template.replace("{{" + entry.getKey() + "}}", HtmlUtils.htmlEscape(entry.getValue()));
        return template;
    }
}
