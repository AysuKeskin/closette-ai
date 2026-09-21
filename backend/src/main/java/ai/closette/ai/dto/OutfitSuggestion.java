package ai.closette.ai.dto;

import java.util.List;

/** The stylist's chosen outfit: item ids (in wear order) + copy. */
public record OutfitSuggestion(
        List<String> itemIds,
        String title,
        String rationale,
        /** How dressy the stylist judged the occasion: casual | smart | formal. */
        String formality,
        /** The season it judged the occasion to fall in: spring | summer | fall | winter. */
        String season
) {
}
