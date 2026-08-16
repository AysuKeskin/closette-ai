package ai.closette.user.repository;

import ai.closette.user.model.StylePreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StylePreferenceRepository extends JpaRepository<StylePreference, UUID> {

    Optional<StylePreference> findByUserId(UUID userId);
}
