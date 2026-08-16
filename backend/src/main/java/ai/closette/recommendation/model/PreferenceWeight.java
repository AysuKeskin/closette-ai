package ai.closette.recommendation.model;

import ai.closette.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A single learned preference weight (FR-09), e.g. attribute="black" weight=0.90.
 * Recomputed from feedback with simple additive weights — no ML training.
 */
@Entity
@Table(name = "preference_weights")
public class PreferenceWeight extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "attribute", nullable = false)
    private String attribute;

    @Column(name = "weight", nullable = false)
    private double weight;

    protected PreferenceWeight() {
    }

    public PreferenceWeight(UUID userId, String attribute, double weight) {
        this.userId = userId;
        this.attribute = attribute;
        this.weight = weight;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAttribute() {
        return attribute;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }
}
