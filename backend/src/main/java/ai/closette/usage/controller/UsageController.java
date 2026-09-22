package ai.closette.usage.controller;

import ai.closette.common.api.ApiResponse;
import ai.closette.common.security.SecurityUtil;
import ai.closette.usage.service.UsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * What the account may still spend.
 *
 * Counts per operation rather than one balance, because that is what a person can
 * act on: "18 outfits left" tells you something, "180 credits" does not, and a
 * shared number would mean reading an ingredient label quietly costs an outfit.
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageService usage;

    public UsageController(UsageService usage) {
        this.usage = usage;
    }

    public record AllowanceResponse(String operation, int remaining, int total, Instant resetsAt) {
    }

    @GetMapping("/me")
    public ApiResponse<List<AllowanceResponse>> me() {
        List<AllowanceResponse> out = usage.balances(SecurityUtil.currentUserId()).stream()
                .map(b -> new AllowanceResponse(
                        b.operation().key(), b.remaining(), b.total(), b.resetsAt()))
                .toList();
        return ApiResponse.ok(out);
    }
}
