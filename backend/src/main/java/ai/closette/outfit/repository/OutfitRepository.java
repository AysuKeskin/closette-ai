package ai.closette.outfit.repository;

import ai.closette.outfit.model.Outfit;
import ai.closette.outfit.model.OutfitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutfitRepository extends JpaRepository<Outfit, UUID> {

    List<Outfit> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Outfit> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, OutfitStatus status);

    List<Outfit> findByUserIdAndFavoriteTrueOrderByCreatedAtDesc(UUID userId);

    Optional<Outfit> findByIdAndUserId(UUID id, UUID userId);
}
