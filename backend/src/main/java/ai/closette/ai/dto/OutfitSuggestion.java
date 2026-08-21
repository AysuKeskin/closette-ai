package ai.closette.ai.dto;

import java.util.List;

/** The stylist's chosen outfit: item ids (in wear order) + copy. */
public record OutfitSuggestion(
        List<String> itemIds,
        String title,
        String rationale
) {
}
