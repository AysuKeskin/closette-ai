package ai.closette.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structured result of analysing a clothing photo. Mirrors the AI service schema.
 * {@code confidence} (0..1) drives the "We think this is a … — is that right?"
 * confirmation UX (NFR-08). {@code colorDetails} carries the richer per-colour
 * breakdown (name + hex + percentage) from the colour pipeline.
 */
public record ClothingAnalysis(
        String category,
        String subcategory,
        List<String> colors,
        @JsonProperty("color_details") List<ColorInfo> colorDetails,
        String pattern,
        List<String> styles,
        List<String> seasons,
        double confidence
) {
}
