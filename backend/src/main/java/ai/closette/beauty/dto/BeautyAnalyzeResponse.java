package ai.closette.beauty.dto;

import ai.closette.ai.dto.BeautyAnalysis;

/**
 * Returned by the beauty analyze step (Flow B): the editable AI suggestion, plus
 * the stored image key/URL so the client can render the photo and later save.
 */
public record BeautyAnalyzeResponse(
        String imageKey,
        String imageUrl,
        BeautyAnalysis analysis
) {
}
