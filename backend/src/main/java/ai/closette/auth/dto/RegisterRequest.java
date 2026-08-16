package ai.closette.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email(message = "A valid email is required")
        String email,

        @NotBlank(message = "Username must not be blank")
        @Pattern(
                regexp = "^[a-zA-Z0-9._]{3,30}$",
                message = "Username must be 3-30 characters, using letters, numbers, . or _")
        String username,

        @NotBlank(message = "Password must not be blank")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$",
                message = "Password must be 8+ characters with an uppercase, a lowercase, and a number")
        String password,

        @Size(max = 80)
        String displayName
) {
}
