package ai.closette.beauty.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * One cached explanation, shared by every user.
 *
 * The language is half the key: the ingredient name is a catalogue value and
 * stays canonical, but the sentence about it is prose and differs per language.
 */
@Entity
@Table(name = "ingredient_explanations")
@IdClass(IngredientExplanationEntity.Key.class)
public class IngredientExplanationEntity {

    @Id
    @Column(name = "inci_name", nullable = false)
    private String inciName;

    @Id
    @Column(nullable = false)
    private String lang;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IngredientExplanationEntity() {
    }

    public IngredientExplanationEntity(String inciName, String lang, String explanation) {
        this.inciName = inciName;
        this.lang = lang;
        this.explanation = explanation;
        this.createdAt = Instant.now();
    }

    public String getInciName() {
        return inciName;
    }

    public String getLang() {
        return lang;
    }

    public String getExplanation() {
        return explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Composite key: the ingredient plus the language its prose is written in. */
    public static class Key implements Serializable {
        private String inciName;
        private String lang;

        public Key() {
        }

        public Key(String inciName, String lang) {
            this.inciName = inciName;
            this.lang = lang;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(inciName, key.inciName) && Objects.equals(lang, key.lang);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inciName, lang);
        }
    }
}
