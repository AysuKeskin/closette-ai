package ai.closette.recommendation.repository;

import ai.closette.recommendation.model.PreferenceWeight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PreferenceWeightRepository extends JpaRepository<PreferenceWeight, UUID> {

    List<PreferenceWeight> findByUserIdOrderByWeightDesc(UUID userId);

    void deleteByUserId(UUID userId);
}
