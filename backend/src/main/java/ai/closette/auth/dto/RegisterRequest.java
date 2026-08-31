package ai.closette.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.username.blank}")
        @Pattern(
                regexp = "^[a-zA-Z0-9._]{3,30}$",
                message = "{validation.username.pattern}")
        String username,

        @NotBlank(message = "{validation.password.blank}")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$",
                message = "{validation.password.pattern}")
        String password,

        @Size(max = 80)
        String displayName
) {
}
