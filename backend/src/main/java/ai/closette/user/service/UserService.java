package ai.closette.user.service;

import ai.closette.common.exception.ApiException;
import ai.closette.user.dto.StylePreferenceResponse;
import ai.closette.user.dto.UpdateProfileRequest;
import ai.closette.user.dto.UpdateStylePreferenceRequest;
import ai.closette.user.dto.UserResponse;
import ai.closette.user.model.StylePreference;
import ai.closette.user.model.User;
import ai.closette.user.repository.StylePreferenceRepository;
import ai.closette.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final StylePreferenceRepository stylePreferenceRepository;

    public UserService(UserRepository userRepository, StylePreferenceRepository stylePreferenceRepository) {
        this.userRepository = userRepository;
        this.stylePreferenceRepository = stylePreferenceRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return UserResponse.from(requireUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().isBlank() ? null : request.displayName().trim());
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public StylePreferenceResponse getPreferences(UUID userId) {
        return StylePreferenceResponse.from(getOrCreate(userId));
    }

    @Transactional
    public StylePreferenceResponse updatePreferences(UUID userId, UpdateStylePreferenceRequest request) {
        StylePreference p = getOrCreate(userId);
        if (request.favoriteColors() != null) {
            p.setFavoriteColors(request.favoriteColors());
        }
        if (request.preferredStyles() != null) {
            p.setPreferredStyles(request.preferredStyles());
        }
        if (request.onboardingCompleted() != null) {
            p.setOnboardingCompleted(request.onboardingCompleted());
        }
        return StylePreferenceResponse.from(stylePreferenceRepository.save(p));
    }

    private StylePreference getOrCreate(UUID userId) {
        return stylePreferenceRepository.findByUserId(userId)
                .orElseGet(() -> stylePreferenceRepository.save(new StylePreference(userId)));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
