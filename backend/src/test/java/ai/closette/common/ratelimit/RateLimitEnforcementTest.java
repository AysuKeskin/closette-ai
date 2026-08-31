package ai.closette.common.ratelimit;

import ai.closette.auth.service.AuthService;
import ai.closette.auth.service.JwtService;
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

import java.util.UUID;

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
        "closette.ratelimit.ip.per-minute=10000",
        "closette.ratelimit.ip.per-day=10000",
        "closette.ratelimit.ip.per-week=10000",
})
class RateLimitEnforcementTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    private String tokenForVerifiedUser() {
        UUID userId = TestData.newUser(authService);
        TestData.markEmailVerified(userRepository, userId);
        return jwtService.generateAccessToken(userId);
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
                        is("Şimdilik yapay zekâ kotanı doldurdun — kısa süre içinde yenilenecek")));
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
}
