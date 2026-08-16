package ai.closette.beauty.repository;

import ai.closette.beauty.model.BeautyCategory;
import ai.closette.beauty.model.BeautyItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BeautyItemRepository extends JpaRepository<BeautyItem, UUID> {

    List<BeautyItem> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<BeautyItem> findByUserIdAndCategoryOrderByCreatedAtDesc(UUID userId, BeautyCategory category);

    Optional<BeautyItem> findByIdAndUserId(UUID id, UUID userId);
}
