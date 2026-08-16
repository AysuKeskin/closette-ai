package ai.closette.outfit.model;

/**
 * Feedback signals with the simple weights used to build a preference profile
 * (FR-09) — no ML training required for the MVP.
 */
public enum FeedbackSignal {
    LOVE(2),
    LIKE(1),
    DISLIKE(-1),
    WORE(2),
    SAVE(1);

    private final int weight;

    FeedbackSignal(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
