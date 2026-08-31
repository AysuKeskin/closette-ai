package ai.closette.outfit.controller;

import ai.closette.common.api.ApiResponse;
import ai.closette.common.ratelimit.RateLimit;
import ai.closette.common.security.SecurityUtil;
import ai.closette.outfit.dto.OutfitDtos.FeedbackRequest;
import ai.closette.outfit.dto.OutfitDtos.GeneratedLook;
import ai.closette.outfit.dto.OutfitDtos.GetReadyRequest;
import ai.closette.outfit.dto.OutfitDtos.OutfitResponse;
import ai.closette.outfit.dto.OutfitDtos.SaveOutfitRequest;
import ai.closette.outfit.model.OutfitStatus;
import ai.closette.outfit.service.OutfitService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/outfits")
public class OutfitController {

    private final OutfitService service;

    public OutfitController(OutfitService service) {
        this.service = service;
    }

    /** FR-07 — Get Ready: generate a complete look from a described occasion. */
    @RateLimit(cost = 8)
    @PostMapping("/generate")
    public ApiResponse<GeneratedLook> generate(@RequestBody GetReadyRequest request) {
        return ApiResponse.ok(service.generate(SecurityUtil.currentUserId(), request));
    }

    @PostMapping
    public ApiResponse<OutfitResponse> save(@Valid @RequestBody SaveOutfitRequest request) {
        return ApiResponse.ok(service.save(SecurityUtil.currentUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<OutfitResponse>> list(
            @RequestParam(required = false) OutfitStatus status,
            @RequestParam(required = false) Boolean favorite) {
        return ApiResponse.ok(service.list(SecurityUtil.currentUserId(), status, favorite));
    }

    @PatchMapping("/{id}/favorite")
    public ApiResponse<OutfitResponse> toggleFavorite(@PathVariable UUID id) {
        return ApiResponse.ok(service.toggleFavorite(SecurityUtil.currentUserId(), id));
    }

    @PatchMapping("/{id}/worn")
    public ApiResponse<OutfitResponse> markWorn(@PathVariable UUID id) {
        return ApiResponse.ok(service.markWorn(SecurityUtil.currentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtil.currentUserId(), id);
        return ApiResponse.ok(null);
    }

    /** FR-09 — record like/love/dislike/save feedback. */
    @PostMapping("/feedback")
    public ApiResponse<Void> feedback(@Valid @RequestBody FeedbackRequest request) {
        service.feedback(SecurityUtil.currentUserId(), request);
        return ApiResponse.ok(null);
    }
}
