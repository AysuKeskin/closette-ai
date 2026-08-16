package ai.closette.outfit.repository;

import ai.closette.outfit.model.OutfitFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutfitFeedbackRepository extends JpaRepository<OutfitFeedback, UUID> {

    List<OutfitFeedback> findByUserId(UUID userId);
}
