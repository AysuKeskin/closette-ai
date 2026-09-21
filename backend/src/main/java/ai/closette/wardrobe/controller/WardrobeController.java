package ai.closette.wardrobe.controller;

import ai.closette.common.api.ApiResponse;
import ai.closette.common.ratelimit.RateLimit;
import ai.closette.common.security.SecurityUtil;
import ai.closette.wardrobe.dto.AnalyzeResponse;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.dto.RetagSummary;
import ai.closette.wardrobe.dto.UpdateItemRequest;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.model.WardrobeFilter;
import ai.closette.wardrobe.service.WardrobeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wardrobe")
public class WardrobeController {

    private final WardrobeService service;

    public WardrobeController(WardrobeService service) {
        this.service = service;
    }

    /** Flow A step 1 — upload a photo, get an editable AI analysis back. */
    @RateLimit(cost = 10)
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AnalyzeResponse> analyze(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.analyze(SecurityUtil.currentUserId(), file));
    }

    /**
     * Re-derive the catalogue tags of pieces already saved.
     *
     * One text call per piece, capped at a batch, so the cost is a fraction of
     * re-analysing every photo — but still enough of it to be worth charging for.
     */
    @RateLimit(cost = 40)
    @PostMapping("/items/retag")
    public ApiResponse<RetagSummary> retag() {
        return ApiResponse.ok(service.retag(SecurityUtil.currentUserId()));
    }

    /** Flow A step 2 — save the confirmed item. */
    @PostMapping("/items")
    public ApiResponse<WardrobeItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        return ApiResponse.ok(service.create(SecurityUtil.currentUserId(), request));
    }

    @GetMapping("/items")
    public ApiResponse<List<WardrobeItemResponse>> list(
            @RequestParam(required = false) ClothingCategory category,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) String q) {
        WardrobeFilter filter = new WardrobeFilter(category, color, season, brand, favorite, q);
        return ApiResponse.ok(service.list(SecurityUtil.currentUserId(), filter));
    }

    @GetMapping("/items/recent")
    public ApiResponse<List<WardrobeItemResponse>> recent(
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(service.recent(SecurityUtil.currentUserId(), limit));
    }

    @GetMapping("/items/{id}")
    public ApiResponse<WardrobeItemResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(SecurityUtil.currentUserId(), id));
    }

    /** Visually similar owned items (pgvector similarity). */
    @GetMapping("/items/{id}/similar")
    public ApiResponse<List<WardrobeItemResponse>> similar(
            @PathVariable UUID id, @RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.ok(service.similar(SecurityUtil.currentUserId(), id, limit));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<WardrobeItemResponse> update(
            @PathVariable UUID id, @RequestBody UpdateItemRequest request) {
        return ApiResponse.ok(service.update(SecurityUtil.currentUserId(), id, request));
    }

    @PatchMapping("/items/{id}/favorite")
    public ApiResponse<WardrobeItemResponse> toggleFavorite(@PathVariable UUID id) {
        return ApiResponse.ok(service.toggleFavorite(SecurityUtil.currentUserId(), id));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtil.currentUserId(), id);
        return ApiResponse.ok(null);
    }
}
