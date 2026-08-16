package ai.closette.user.controller;

import ai.closette.auth.dto.VerifyEmailRequest;
import ai.closette.auth.service.EmailVerificationService;
import ai.closette.common.api.ApiResponse;
import ai.closette.common.security.SecurityUtil;
import ai.closette.user.dto.StylePreferenceResponse;
import ai.closette.user.dto.UpdateProfileRequest;
import ai.closette.user.dto.UpdateStylePreferenceRequest;
import ai.closette.user.dto.UserResponse;
import ai.closette.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    public UserController(UserService userService, EmailVerificationService emailVerificationService) {
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(userService.getProfile(SecurityUtil.currentUserId()));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(SecurityUtil.currentUserId(), request));
    }

    /** Soft email verification — submit the emailed 6-digit code. */
    @PostMapping("/me/email/verify")
    public ApiResponse<UserResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ApiResponse.ok(emailVerificationService.verify(SecurityUtil.currentUserId(), request.code()));
    }

    /** Re-send a fresh verification code (rate-limited). */
    @PostMapping("/me/email/resend")
    public ApiResponse<Void> resendVerification() {
        emailVerificationService.resend(SecurityUtil.currentUserId());
        return ApiResponse.ok(null);
    }

    @GetMapping("/me/style-preferences")
    public ApiResponse<StylePreferenceResponse> preferences() {
        return ApiResponse.ok(userService.getPreferences(SecurityUtil.currentUserId()));
    }

    @PutMapping("/me/style-preferences")
    public ApiResponse<StylePreferenceResponse> updatePreferences(
            @RequestBody UpdateStylePreferenceRequest request) {
        return ApiResponse.ok(userService.updatePreferences(SecurityUtil.currentUserId(), request));
    }
}
