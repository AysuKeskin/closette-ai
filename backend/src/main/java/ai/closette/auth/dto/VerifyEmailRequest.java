package ai.closette.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
        @NotBlank(message = "{validation.code.required}")
        @Pattern(regexp = "^\\d{6}$", message = "{validation.code.sixDigits}")
        String code
) {
}
