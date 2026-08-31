package ai.closette.recommendation.dto;

import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.dto.WardrobeItemResponse;

import java.util.List;

/** DTOs for preference profile (FR-09) and Should I Buy This (FR-10). */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record PreferenceEntry(String attribute, double weight) {
    }

    /** FR-10 request — structured attributes (derived from a description or photo, not a user form). */
    public record ShouldIBuyRequest(
            ClothingCategory category,
            List<String> colors,
            List<String> styles
    ) {
    }

    /** FR-10 natural-language request: the user describes the item in words. */
    public record DescribeItemRequest(
            String description
    ) {
    }

    /**
     * FR-10 explainable result (NFR-07): a score plus the reasons behind it. The
     * {@code detected*} fields echo what we understood from the words/photo, so the UI
     * can show "Got it — a beige blazer" and the user can trust (or redo) the read.
     */
    public record ShouldIBuyResponse(
            int matchScore,
            String verdict,          // buy | maybe | skip (LLM, RAG-grounded)
            int matchingItemCount,
            int similarItemCount,
            List<WardrobeItemResponse> similarItems,
            String explanation,
            String detectedLabel,
            List<String> detectedColors,
            List<String> detectedStyles,
            // false when the AI couldn't tell what the item is — the UI shows a "couldn't read that"
            // state instead of a fabricated verdict/score.
            boolean understood
    ) {
    }
}
