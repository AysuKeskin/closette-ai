package ai.closette.beauty.model;

import ai.closette.common.persistence.BaseEntity;
import ai.closette.common.persistence.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "beauty_items")
public class BeautyItem extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "brand")
    private String brand;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private BeautyCategory category;

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "size")
    private String size;

    @Convert(converter = StringListConverter.class)
    @Column(name = "ingredients", length = 4000)
    private List<String> ingredients = new ArrayList<>();

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "opened_date")
    private LocalDate openedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Period-after-opening, in months. */
    @Column(name = "pao_months")
    private Integer paoMonths;

    /** Rough amount remaining, 0-100 (%). */
    @Column(name = "amount_remaining")
    private Integer amountRemaining;

    @Column(name = "favorite", nullable = false)
    private boolean favorite = false;

    protected BeautyItem() {
    }

    public BeautyItem(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BeautyCategory getCategory() {
        return category;
    }

    public void setCategory(BeautyCategory category) {
        this.category = category;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public List<String> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<String> ingredients) {
        this.ingredients = ingredients;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public LocalDate getOpenedDate() {
        return openedDate;
    }

    public void setOpenedDate(LocalDate openedDate) {
        this.openedDate = openedDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Integer getPaoMonths() {
        return paoMonths;
    }

    public void setPaoMonths(Integer paoMonths) {
        this.paoMonths = paoMonths;
    }

    public Integer getAmountRemaining() {
        return amountRemaining;
    }

    public void setAmountRemaining(Integer amountRemaining) {
        this.amountRemaining = amountRemaining;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }
}
