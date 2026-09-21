package ai.closette.wardrobe.dto;

/**
 * What one re-catalogue run did.
 *
 * {@code remaining} is what the batch cap left behind, so the app can tell the user
 * whether running it again has anything left to do.
 */
public record RetagSummary(
        int examined,
        int updated,
        int failed,
        int remaining
) {
}
