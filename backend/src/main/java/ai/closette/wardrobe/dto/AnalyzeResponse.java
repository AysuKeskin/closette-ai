package ai.closette.wardrobe.dto;

import ai.closette.ai.dto.ClothingAnalysis;

/**
 * Returned by the Flow A analyze step: the editable AI suggestion, plus the stored
 * image key/URL so the client can render the photo and later save the item.
 */
public record AnalyzeResponse(
        String imageKey,
        String imageUrl,
        ClothingAnalysis analysis
) {
}
