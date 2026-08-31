package ai.closette.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.code.required}")
        @Pattern(regexp = "^\\d{6}$", message = "{validation.code.sixDigits}")
        String code,

        @NotBlank(message = "{validation.password.blank}")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$",
                message = "{validation.password.pattern}")
        String newPassword
) {
}
