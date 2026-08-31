package ai.closette.recommendation.controller;

import ai.closette.common.api.ApiResponse;
import ai.closette.common.ratelimit.RateLimit;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.common.exception.ErrorCode;
import ai.closette.common.security.SecurityUtil;
import org.springframework.http.HttpStatus;
import ai.closette.recommendation.dto.RecommendationDtos.DescribeItemRequest;
import ai.closette.recommendation.dto.RecommendationDtos.PreferenceEntry;
import ai.closette.recommendation.dto.RecommendationDtos.ShouldIBuyResponse;
import ai.closette.recommendation.service.RecommendationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    /** FR-10 — Should I buy this? Natural-language: the user describes the item in words. */
    @RateLimit(cost = 4)
    @PostMapping("/should-i-buy")
    public ApiResponse<ShouldIBuyResponse> shouldIBuy(@RequestBody DescribeItemRequest request) {
        return ApiResponse.ok(service.shouldIBuyFromDescription(SecurityUtil.currentUserId(), request.description()));
    }

    /** FR-10 — Should I buy this? Photo: the VLM reads the item straight from a picture. */
    @RateLimit(cost = 12)
    @PostMapping(value = "/should-i-buy/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ShouldIBuyResponse> shouldIBuyFromPhoto(@RequestParam("file") MultipartFile file) {
        try {
            return ApiResponse.ok(service.shouldIBuyFromPhoto(SecurityUtil.currentUserId(),
                    file.getBytes(), file.getOriginalFilename(), file.getContentType()));
        } catch (IOException e) {
            throw ApiException.validation(MessageKeys.IMAGE_UNREADABLE);
        }
    }
}
