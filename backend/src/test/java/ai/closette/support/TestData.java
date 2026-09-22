package ai.closette.support;

import ai.closette.auth.dto.AuthResponse;
import ai.closette.auth.dto.RegisterRequest;
import ai.closette.auth.service.AuthService;
import ai.closette.user.model.User;
import ai.closette.user.repository.UserRepository;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.model.ClothingCategory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared arrangement helpers. The H2 database is shared by every test in the
 * context, so each test creates its own user and relies on the same user-id
 * scoping the production code uses — no cleanup between tests needed.
 */
public final class TestData {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestData() {
    }

    /** Registers a fresh, unverified user and returns its id. */
    public static UUID newUser(AuthService authService) {
        return register(authService).user().id();
    }

    /**
     * Same registration, but the whole response. Access tokens are only accepted
     * while the session that issued them is alive, so a test calling an endpoint
     * over HTTP needs the token registration handed out, not one minted from the
     * user id alone.
     */
    public static AuthResponse register(AuthService authService) {
        long n = SEQUENCE.incrementAndGet();
        return authService.register(new RegisterRequest(
                "user" + n + "." + System.nanoTime() + "@test.io",
                "user_" + n + "_" + System.nanoTime(),
                "Password123",
                "Test User"));
    }

    /**
     * Marks an account verified directly. Features gated on verification need a
     * verified user as a precondition; the verification flow itself is covered by
     * EmailVerificationServiceTest.
     */
    public static void markEmailVerified(UserRepository userRepository, UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    public static CreateItemRequest item(String name, ClothingCategory category,
                                         List<String> colors, List<String> styles) {
        return item(name, category, colors, styles, List.of("spring"));
    }

    /** Same, with the seasons spelled out — for tests about when a piece is worn. */
    public static CreateItemRequest item(String name, ClothingCategory category, List<String> colors,
                                         List<String> styles, List<String> seasons) {
        return new CreateItemRequest(name, category, null, colors, null, "solid", styles,
                seasons, null, null, null, false);
    }
}
