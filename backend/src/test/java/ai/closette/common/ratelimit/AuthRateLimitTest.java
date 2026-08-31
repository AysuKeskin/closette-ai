package ai.closette.common.ratelimit;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The auth endpoints under enforcement.
 *
 * These are the requests nobody has to sign in to make, so they are the ones a
 * script can point at all day: account farming, credential stuffing, and
 * password-reset mail that costs real money to send.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "closette.ratelimit.mode=enforce",
        "closette.ratelimit.register-ip.per-minute=2",
        "closette.ratelimit.register-ip.per-day=100",
        "closette.ratelimit.register-ip.per-week=100",
        "closette.ratelimit.login-ip.per-minute=1000",
        "closette.ratelimit.login-ip.per-day=1000",
        "closette.ratelimit.login-ip.per-week=1000",
        "closette.ratelimit.login-email.per-minute=2",
        "closette.ratelimit.login-email.per-day=100",
        "closette.ratelimit.login-email.per-week=100",
        "closette.ratelimit.password-reset-ip.per-minute=1000",
        "closette.ratelimit.password-reset-ip.per-day=1000",
        "closette.ratelimit.password-reset-ip.per-week=1000",
        "closette.ratelimit.password-reset-email.per-minute=1",
        "closette.ratelimit.password-reset-email.per-day=100",
        "closette.ratelimit.password-reset-email.per-week=100",
})
class AuthRateLimitTest {

    @Autowired
    MockMvc mockMvc;

    private String registration() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return """
                {"email":"rl-%s@test.io","username":"rl_%s","password":"Password123","displayName":"RL"}
                """.formatted(unique, unique);
    }

    private void register(String body) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void accountFarmingFromOneAddressIsCutOff() throws Exception {
        register(registration());
        register(registration());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registration()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code", is("RATE_LIMITED")));
    }

    @Test
    void guessingAtOneEmailIsCutOffEvenWithRoomOnTheIp() throws Exception {
        // The IP budget here is huge on purpose: what stops this is the per-email
        // limit, which is the one an attacker concentrating on one victim hits.
        String body = """
                {"email":"victim@test.io","password":"WrongPassword1"}
                """;
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void anotherEmailStillGetsToTryFromTheSameAddress() throws Exception {
        String victim = """
                {"email":"victim2@test.io","password":"WrongPassword1"}
                """;
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON).content(victim));
        }

        // A per-email limit must not become a per-IP one: everyone else behind
        // that address still has to be able to sign in.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone-else@test.io\",\"password\":\"WrongPassword1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetMailCannotBeUsedToBombOneInbox() throws Exception {
        // Each of these would send a real email, so the limit is about the bill
        // as much as the nuisance.
        String body = """
                {"email":"target@test.io"}
                """;
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void theRefusalIsTranslated() throws Exception {
        String body = """
                {"email":"target-tr@test.io"}
                """;
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON).content(body));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.message", is("Çok fazla deneme — biraz bekleyip tekrar dene")));
    }
}
