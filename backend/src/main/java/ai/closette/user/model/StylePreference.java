package ai.closette.user.model;

import ai.closette.common.persistence.BaseEntity;
import ai.closette.common.persistence.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A user's editable style preferences (FR-01.3). Kept as a separate row keyed by
 * user id; lists are stored comma-separated via {@link StringListConverter}.
 */
@Entity
@Table(name = "style_preferences")
public class StylePreference extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Convert(converter = StringListConverter.class)
    @Column(name = "favorite_colors", length = 1000)
    private List<String> favoriteColors = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "preferred_styles", length = 1000)
    private List<String> preferredStyles = new ArrayList<>();

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    protected StylePreference() {
    }

    public StylePreference(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public List<String> getFavoriteColors() {
        return favoriteColors;
    }

    public void setFavoriteColors(List<String> favoriteColors) {
        this.favoriteColors = favoriteColors;
    }

    public List<String> getPreferredStyles() {
        return preferredStyles;
    }

    public void setPreferredStyles(List<String> preferredStyles) {
        this.preferredStyles = preferredStyles;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public void setOnboardingCompleted(boolean onboardingCompleted) {
        this.onboardingCompleted = onboardingCompleted;
    }
}
