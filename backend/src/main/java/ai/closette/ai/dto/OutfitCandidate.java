package ai.closette.ai.dto;

import java.util.List;

/** One owned item passed to the stylist LLM as retrieved context (RAG). */
public record OutfitCandidate(
        String id,
        String name,
        String category,
        String subcategory,
        List<String> colors,
        /**
         * The colour covering at least half the piece, or null when none does.
         *
         * A garment listing navy and white reads differently depending on which
         * is the cloth and which is the trim, and that is what a look has to
         * harmonise around. Null is itself an answer: nothing dominates, so the
         * piece is genuinely multicoloured.
         */
        String dominantColor,
        List<String> styles,
        List<String> seasons
) {
}
