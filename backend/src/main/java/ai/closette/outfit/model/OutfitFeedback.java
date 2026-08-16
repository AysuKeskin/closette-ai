package ai.closette.outfit.model;

import ai.closette.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A single feedback event on a look (FR-09). Aggregated later into preference weights.
 */
@Entity
@Table(name = "outfit_feedback")
public class OutfitFeedback extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "outfit_id")
    private UUID outfitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal", nullable = false, length = 16)
    private FeedbackSignal signal;

    protected OutfitFeedback() {
    }

    public OutfitFeedback(UUID userId, UUID outfitId, FeedbackSignal signal) {
        this.userId = userId;
        this.outfitId = outfitId;
        this.signal = signal;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOutfitId() {
        return outfitId;
    }

    public FeedbackSignal getSignal() {
        return signal;
    }
}
