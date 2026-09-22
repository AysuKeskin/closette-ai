package ai.closette.wardrobe.model;

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

@Entity
@Table(name = "wardrobe_items")
public class WardrobeItem extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private ClothingCategory category;

    @Column(name = "subcategory")
    private String subcategory;

    @Convert(converter = StringListConverter.class)
    @Column(name = "colors", length = 500)
    private List<String> colors = new ArrayList<>();

    /**
     * Measured shares as "name:percent", in the same order as {@link #colors}.
     *
     * Empty whenever the colours were edited by hand: a share read off a photo
     * describes that photo, and once somebody types a colour in there is nothing
     * honest to say about how much of the garment it covers.
     */
    @Convert(converter = StringListConverter.class)
    @Column(name = "color_shares", length = 500)
    private List<String> colorShares = new ArrayList<>();

    @Column(name = "pattern")
    private String pattern;

    @Convert(converter = StringListConverter.class)
    @Column(name = "styles", length = 500)
    private List<String> styles = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "seasons", length = 200)
    private List<String> seasons = new ArrayList<>();

    @Column(name = "brand")
    private String brand;

    @Column(name = "size")
    private String size;

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "favorite", nullable = false)
    private boolean favorite = false;

    protected WardrobeItem() {
    }

    public WardrobeItem(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ClothingCategory getCategory() {
        return category;
    }

    public void setCategory(ClothingCategory category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public List<String> getColors() {
        return colors;
    }

    public void setColors(List<String> colors) {
        this.colors = colors;
    }

    public List<String> getColorShares() {
        return colorShares;
    }

    public void setColorShares(List<String> colorShares) {
        this.colorShares = colorShares == null ? new ArrayList<>() : colorShares;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public List<String> getStyles() {
        return styles;
    }

    public void setStyles(List<String> styles) {
        this.styles = styles;
    }

    public List<String> getSeasons() {
        return seasons;
    }

    public void setSeasons(List<String> seasons) {
        this.seasons = seasons;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }
}
