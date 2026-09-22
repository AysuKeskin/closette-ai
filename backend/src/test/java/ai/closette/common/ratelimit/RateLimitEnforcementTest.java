package ai.closette.common.ratelimit;

import ai.closette.auth.dto.AuthResponse;
import ai.closette.auth.service.AuthService;
import ai.closette.support.TestData;
import ai.closette.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The limiter over HTTP, on a real annotated endpoint.
 *
 * The budget here is tiny and enforcement is on, so the interceptor's wiring —
 * annotation found, cost charged, 429 shaped correctly — is what gets proven,
 * not the arithmetic (that is {@link RateLimiterTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "closette.ratelimit.mode=enforce",
        // Get Ready costs 8, so the second call cannot fit in a 10-unit day.
        "closette.ratelimit.user.per-minute=1000",
        "closette.ratelimit.user.per-day=10",
        "closette.ratelimit.user.per-week=1000",
})
class RateLimitEnforcementTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    private String tokenForVerifiedUser() {
        AuthResponse auth = TestData.register(authService);
        TestData.markEmailVerified(userRepository, auth.user().id());
        return auth.accessToken();
    }

    @Test
    void aSecondCallThatDoesNotFitTheBudgetIsRefused() throws Exception {
        String token = tokenForVerifiedUser();

        mockMvc.perform(post("/api/outfits/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occasion\":\"dinner\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/outfits/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occasion\":\"dinner\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code", is("RATE_LIMITED")));
    }

    @Test
    void theRefusalIsTranslatedLikeEveryOtherError() throws Exception {
        String token = tokenForVerifiedUser();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/outfits/generate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"occasion\":\"akşam yemeği\"}"));
        }

        mockMvc.perform(post("/api/outfits/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occasion\":\"akşam yemeği\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.message",
                        is("Şimdilik yapay zekâ kotanı doldurdun.")))
                // The app appends the concrete wait from this header; the sentence
                // stopped promising "shortly" once the real number was available.
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void anUnannotatedEndpointIsNotCharged() throws Exception {
        // Only endpoints that spend tokens carry a cost; listing the wardrobe is
        // free and must stay free however many times it is called.
        String token = tokenForVerifiedUser();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/outfits/feedback")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"signal\":\"LIKE\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void oneUserSpendingTheirBudgetDoesNotTouchAnotherOnTheSameAddress() throws Exception {
        // Both requests arrive from the test client's single address. Turkish mobile
        // carriers put thousands of real subscribers behind one, so charging the
        // address would have the first active user spend the whole street's day.
        String first = tokenForVerifiedUser();
        String second = tokenForVerifiedUser();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/outfits/generate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"occasion\":\"dinner\"}"));
        }

        mockMvc.perform(post("/api/outfits/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occasion\":\"dinner\"}"))
                .andExpect(status().isOk());
    }
}
