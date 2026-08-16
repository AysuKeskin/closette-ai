package ai.closette.wardrobe.repository;

import ai.closette.wardrobe.model.WardrobeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WardrobeItemRepository
        extends JpaRepository<WardrobeItem, UUID>, JpaSpecificationExecutor<WardrobeItem> {

    Optional<WardrobeItem> findByIdAndUserId(UUID id, UUID userId);

    List<WardrobeItem> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);
}
