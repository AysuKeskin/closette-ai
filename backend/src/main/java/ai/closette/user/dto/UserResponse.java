package ai.closette.user.dto;

import ai.closette.user.model.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        boolean emailVerified,
        // When the code now in the user's inbox stops working. Null once the
        // account is verified, or if no code is outstanding. The client counts
        // down to it; only the server knows when the code was actually issued,
        // so a timer started on the client would be wrong for anyone who comes
        // back to the screen later.
        Instant verificationExpiresAt,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.isEmailVerified(),
                user.isEmailVerified() ? null : user.getVerificationExpiresAt(),
                user.getCreatedAt());
    }
}
