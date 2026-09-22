package ai.closette.beauty.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A product seen from an external catalogue, kept so the next search for it does
 * not have to leave the building.
 *
 * Shared by every user and not scoped by one: this is public catalogue data, the
 * same for everyone who looks it up. What a given person owns stays in
 * {@link BeautyItem}, where the user scoping rule applies as usual.
 */
@Entity
@Table(name = "beauty_catalogue")
public class CatalogueProduct {

    @Id
    @Column(nullable = false, length = 64)
    private String barcode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private BeautyCategory category;

    @Column(columnDefinition = "text")
    private String ingredients;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    protected CatalogueProduct() {
    }

    public CatalogueProduct(String barcode, String productName, String brand, BeautyCategory category,
                            String ingredients, String imageUrl, String source) {
        this.barcode = barcode;
        this.productName = productName;
        this.brand = brand;
        this.category = category;
        this.ingredients = ingredients;
        this.imageUrl = imageUrl;
        this.source = source;
        this.fetchedAt = Instant.now();
    }

    public String getBarcode() {
        return barcode;
    }

    public String getProductName() {
        return productName;
    }

    public String getBrand() {
        return brand;
    }

    public BeautyCategory getCategory() {
        return category;
    }

    public String getIngredients() {
        return ingredients;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
