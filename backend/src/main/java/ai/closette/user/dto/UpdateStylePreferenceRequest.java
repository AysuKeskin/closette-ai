package ai.closette.user.dto;

import java.util.List;

public record UpdateStylePreferenceRequest(
        List<String> favoriteColors,
        List<String> preferredStyles,
        Boolean onboardingCompleted
) {
}
