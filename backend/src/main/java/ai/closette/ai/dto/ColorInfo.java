package ai.closette.ai.dto;

/**
 * A dominant garment colour extracted by the AI service's colour pipeline
 * (classic code — LAB nearest fashion colour, not the VLM).
 */
public record ColorInfo(
        String name,
        String hex,
        int percentage
) {
}
