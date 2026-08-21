package ai.closette.ai.dto;

import java.util.List;

/** One owned item passed to the stylist LLM as retrieved context (RAG). */
public record OutfitCandidate(
        String id,
        String name,
        String category,
        String subcategory,
        List<String> colors,
        List<String> styles,
        List<String> seasons
) {
}
