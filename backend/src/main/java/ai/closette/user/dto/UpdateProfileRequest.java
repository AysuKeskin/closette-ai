package ai.closette.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 80, message = "Display name is too long")
        String displayName
) {
}
