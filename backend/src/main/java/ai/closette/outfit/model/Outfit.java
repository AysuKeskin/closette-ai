package ai.closette.outfit.model;

import ai.closette.common.persistence.BaseEntity;
import ai.closette.common.persistence.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A saved look (FR-14). Member wardrobe item ids are stored as a simple id list.
 */
@Entity
@Table(name = "outfits")
public class Outfit extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title")
    private String title;

    @Column(name = "occasion")
    private String occasion;

    @Convert(converter = StringListConverter.class)
    @Column(name = "item_ids", length = 2000)
    private List<String> itemIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutfitStatus status = OutfitStatus.SAVED;

    @Column(name = "favorite", nullable = false)
    private boolean favorite = false;

    protected Outfit() {
    }

    public Outfit(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOccasion() {
        return occasion;
    }

    public void setOccasion(String occasion) {
        this.occasion = occasion;
    }

    public List<String> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<String> itemIds) {
        this.itemIds = itemIds;
    }

    public OutfitStatus getStatus() {
        return status;
    }

    public void setStatus(OutfitStatus status) {
        this.status = status;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }
}
