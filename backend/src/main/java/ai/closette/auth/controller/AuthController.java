package ai.closette.auth.controller;

import ai.closette.auth.dto.AuthResponse;
import ai.closette.auth.dto.ForgotPasswordRequest;
import ai.closette.auth.dto.LoginRequest;
import ai.closette.auth.dto.RefreshRequest;
import ai.closette.auth.dto.RegisterRequest;
import ai.closette.auth.dto.ResetPasswordRequest;
import ai.closette.auth.service.AuthService;
import ai.closette.common.api.ApiResponse;
import ai.closette.common.ratelimit.RateLimit;
import ai.closette.common.ratelimit.RateLimitBucket;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @RateLimit(bucket = RateLimitBucket.REGISTER, cost = 1)

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @RateLimit(bucket = RateLimitBucket.LOGIN, cost = 1)

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    /** Start a password reset — emails a code if the account exists. */
    @RateLimit(bucket = RateLimitBucket.PASSWORD_RESET, cost = 1)
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.ok(null);
    }

    /** Complete a password reset with the emailed code and a new password. */
    @RateLimit(bucket = RateLimitBucket.PASSWORD_RESET, cost = 1)
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok(null);
    }

    /** Stateless logout — the client simply discards its tokens. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null);
    }
}
