package ai.closette.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 80, message = "{validation.displayName.tooLong}")
        String displayName
) {
}
