package ai.closette.user.dto;

import ai.closette.user.model.StylePreference;

import java.util.List;

public record StylePreferenceResponse(
        List<String> favoriteColors,
        List<String> preferredStyles,
        boolean onboardingCompleted
) {

    public static StylePreferenceResponse from(StylePreference p) {
        return new StylePreferenceResponse(p.getFavoriteColors(), p.getPreferredStyles(), p.isOnboardingCompleted());
    }
}
