package ai.closette.common.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The language actually reaching a response, over HTTP.
 *
 * The service tests assert message keys; this asserts the wiring around them —
 * that {@code Accept-Language} is resolved, that an unsupported language falls
 * back to English, and that the machine-readable half of the envelope never moves.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AcceptLanguageTest {

    private static final String BAD_LOGIN = """
            {"email":"nobody-%s@test.io","password":"Password123"}
            """;

    @Autowired
    MockMvc mockMvc;

    private String badLogin() {
        return BAD_LOGIN.formatted(System.nanoTime());
    }

    @Test
    void anErrorAnswersInTurkishWhenTheClientAsksForTurkish() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("E-posta veya şifre hatalı")));
    }

    @Test
    void theSameErrorAnswersInEnglishByDefault() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Invalid email or password")));
    }

    @Test
    void aRegionalTurkishTagStillResolvesToTurkish() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9,en;q=0.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin()))
                .andExpect(jsonPath("$.error.message", is("E-posta veya şifre hatalı")));
    }

    @Test
    void anUnsupportedLanguageFallsBackToEnglish() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin()))
                .andExpect(jsonPath("$.error.message", is("Invalid email or password")));
    }

    @Test
    void formValidationMessagesAreTranslatedToo() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION")))
                // Field prefixes stay English so the app can place each message
                // under its input; only the sentence after the colon changes.
                .andExpect(jsonPath("$.error.message", containsString("password:")))
                .andExpect(jsonPath("$.error.message", containsString("Şifre")))
                .andExpect(jsonPath("$.error.message", not(containsString("Password must"))));
    }

    @Test
    void unauthenticatedAccessIsRejectedInTheRequestedLanguage() throws Exception {
        mockMvc.perform(post("/api/wardrobe/items")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message", is("Devam etmek için giriş yap")));
    }
}
