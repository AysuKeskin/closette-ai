package ai.closette.user.service;

import ai.closette.auth.service.AuthService;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.support.TestData;
import ai.closette.user.dto.StylePreferenceResponse;
import ai.closette.user.dto.UpdateProfileRequest;
import ai.closette.user.dto.UpdateStylePreferenceRequest;
import ai.closette.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Profile and style preferences (FR-01.3). */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {

    @Autowired
    AuthService authService;

    @Autowired
    UserService userService;

    private UUID newUser() {
        return TestData.newUser(authService);
    }

    @Test
    void profileReflectsTheRegisteredAccount() {
        UUID userId = newUser();

        UserResponse profile = userService.getProfile(userId);

        assertThat(profile.id()).isEqualTo(userId);
        assertThat(profile.email()).endsWith("@test.io");
        assertThat(profile.emailVerified()).isFalse();
    }

    @Test
    void displayNameIsTrimmedOnUpdate() {
        UUID userId = newUser();

        assertThat(userService.updateProfile(userId, new UpdateProfileRequest("  Aysu  ")).displayName())
                .isEqualTo("Aysu");
    }

    @Test
    void clearingTheDisplayNameStoresNullRatherThanAnEmptyString() {
        UUID userId = newUser();
        userService.updateProfile(userId, new UpdateProfileRequest("Aysu"));

        assertThat(userService.updateProfile(userId, new UpdateProfileRequest("   ")).displayName()).isNull();
    }

    @Test
    void omittingTheDisplayNameLeavesItUnchanged() {
        // null means "not sent", which is different from "cleared".
        UUID userId = newUser();
        userService.updateProfile(userId, new UpdateProfileRequest("Aysu"));

        assertThat(userService.updateProfile(userId, new UpdateProfileRequest(null)).displayName())
                .isEqualTo("Aysu");
    }

    @Test
    void preferencesAreCreatedOnFirstReadWithEmptyDefaults() {
        StylePreferenceResponse preferences = userService.getPreferences(newUser());

        assertThat(preferences.favoriteColors()).isEmpty();
        assertThat(preferences.preferredStyles()).isEmpty();
        assertThat(preferences.onboardingCompleted()).isFalse();
    }

    @Test
    void preferencesRoundTripThroughTheCommaSeparatedColumns() {
        UUID userId = newUser();

        userService.updatePreferences(userId, new UpdateStylePreferenceRequest(
                List.of("dusty pink", "navy"), List.of("minimal", "classic"), true));

        StylePreferenceResponse stored = userService.getPreferences(userId);
        assertThat(stored.favoriteColors()).containsExactly("dusty pink", "navy");
        assertThat(stored.preferredStyles()).containsExactly("minimal", "classic");
        assertThat(stored.onboardingCompleted()).isTrue();
    }

    @Test
    void updatingPreferencesOnlyTouchesTheFieldsThatWereSent() {
        UUID userId = newUser();
        userService.updatePreferences(userId, new UpdateStylePreferenceRequest(
                List.of("navy"), List.of("minimal"), true));

        StylePreferenceResponse updated = userService.updatePreferences(userId,
                new UpdateStylePreferenceRequest(List.of("black"), null, null));

        assertThat(updated.favoriteColors()).containsExactly("black");
        assertThat(updated.preferredStyles()).containsExactly("minimal");
        assertThat(updated.onboardingCompleted()).isTrue();
    }

    @Test
    void preferencesAreScopedToTheirOwner() {
        UUID owner = newUser();
        UUID stranger = newUser();
        userService.updatePreferences(owner, new UpdateStylePreferenceRequest(
                List.of("navy"), List.of("minimal"), true));

        assertThat(userService.getPreferences(stranger).favoriteColors()).isEmpty();
    }

    @Test
    void anUnknownUserIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> userService.getProfile(unknown))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> userService.updateProfile(unknown, new UpdateProfileRequest("Aysu")))
                .isInstanceOf(ApiException.class);
    }
}
