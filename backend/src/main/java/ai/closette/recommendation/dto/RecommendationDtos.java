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

    /** FR-10 request — either typed in, or prefilled from a photo analysis. */
    public record ShouldIBuyRequest(
            ClothingCategory category,
            List<String> colors,
            List<String> styles
    ) {
    }

    /** FR-10 explainable result (NFR-07): a score plus the reasons behind it. */
    public record ShouldIBuyResponse(
            int matchScore,
            String verdict,          // buy | maybe | skip (LLM, RAG-grounded)
            int matchingItemCount,
            int similarItemCount,
            List<WardrobeItemResponse> similarItems,
            String explanation
    ) {
    }
}
