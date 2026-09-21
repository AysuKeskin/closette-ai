package ai.closette.auth.service;

import ai.closette.config.PrivacyProperties;
import ai.closette.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = {"closette.privacy.ai-provider=external-test-provider"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AiConsentTest {
    @Autowired AuthService auth;
    @Autowired AiConsentService consent;
    @Autowired PrivacyProperties privacy;
    @Autowired MockMvc mvc;

    @Test
    void externalAiCallsRequireConsentAndWithdrawalBlocksThemAgain() throws Exception {
        var user = TestData.register(auth);
        mvc.perform(post("/api/outfits/generate").header("Authorization", "Bearer " + user.accessToken())
                .contentType("application/json").content("{\"occasion\":\"dinner\"}"))
                .andExpect(status().isForbidden());
        consent.update(user.user().id(), privacy.consentVersion(), true);
        consent.require(user.user().id());
        consent.update(user.user().id(), null, false);
        mvc.perform(post("/api/outfits/generate").header("Authorization", "Bearer " + user.accessToken())
                .contentType("application/json").content("{\"occasion\":\"dinner\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStaleDisclosureCannotBeAccepted() throws Exception {
        var user = TestData.register(auth);
        mvc.perform(put("/api/users/me/ai-consent").header("Authorization", "Bearer " + user.accessToken())
                .contentType("application/json").content("{\"version\":\"old-version\",\"accepted\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void policyAndSupportArePublicAndBilingual() throws Exception {
        mvc.perform(get("/privacy").param("lang", "tr"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("gizlilik politikası")));
        mvc.perform(get("/support"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Closette support")));
        mvc.perform(get("/api/privacy"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiresConsent").value(true));
    }
}
