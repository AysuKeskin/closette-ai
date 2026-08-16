package ai.closette.ai.dto;

public record BeautyAnalysis(
        String brand,
        String productName,
        String category,
        double confidence
) {
}
