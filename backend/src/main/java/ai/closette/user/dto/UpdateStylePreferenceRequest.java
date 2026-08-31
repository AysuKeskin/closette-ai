package ai.closette.user.dto;

import java.util.List;

public record UpdateStylePreferenceRequest(
        List<String> favoriteColors,
        List<String> preferredStyles,
        String colorSeason,
        List<String> lovedAesthetics,
        String dressUp,
        Boolean onboardingCompleted
) {
}
