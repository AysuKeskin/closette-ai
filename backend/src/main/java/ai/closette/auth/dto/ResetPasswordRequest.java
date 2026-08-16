package ai.closette.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank @Email(message = "A valid email is required")
        String email,

        @NotBlank(message = "Enter the 6-digit code")
        @Pattern(regexp = "^\\d{6}$", message = "The code is 6 digits")
        String code,

        @NotBlank(message = "Password must not be blank")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$",
                message = "Password must be 8+ characters with an uppercase, a lowercase, and a number")
        String newPassword
) {
}
