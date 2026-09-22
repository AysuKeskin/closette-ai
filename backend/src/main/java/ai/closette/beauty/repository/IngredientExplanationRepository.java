package ai.closette.beauty.repository;

import ai.closette.beauty.model.IngredientExplanationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Cached ingredient prose. Not scoped by user on purpose: an explanation of
 * "Niacinamide" is the same for everyone, and paying the model once per person
 * for the same sentence is the cost this table exists to remove.
 */
public interface IngredientExplanationRepository
        extends JpaRepository<IngredientExplanationEntity, IngredientExplanationEntity.Key> {

    Optional<IngredientExplanationEntity> findByInciNameIgnoreCaseAndLang(String inciName, String lang);
}
