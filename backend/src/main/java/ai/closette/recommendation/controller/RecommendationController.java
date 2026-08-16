package ai.closette.recommendation.controller;

import ai.closette.common.api.ApiResponse;
import ai.closette.common.security.SecurityUtil;
import ai.closette.recommendation.dto.RecommendationDtos.PreferenceEntry;
import ai.closette.recommendation.dto.RecommendationDtos.ShouldIBuyRequest;
import ai.closette.recommendation.dto.RecommendationDtos.ShouldIBuyResponse;
import ai.closette.recommendation.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    /** FR-09 — current preference profile. */
    @GetMapping("/preferences")
    public ApiResponse<List<PreferenceEntry>> preferences() {
        return ApiResponse.ok(service.recomputeProfile(SecurityUtil.currentUserId()));
    }

    /** FR-10 — Should I buy this? */
    @PostMapping("/should-i-buy")
    public ApiResponse<ShouldIBuyResponse> shouldIBuy(@RequestBody ShouldIBuyRequest request) {
        return ApiResponse.ok(service.shouldIBuy(SecurityUtil.currentUserId(), request));
    }
}
