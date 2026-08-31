package ai.closette.beauty.controller;

import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.beauty.model.BeautyCategory;
import ai.closette.beauty.service.BeautyService;
import ai.closette.common.api.ApiResponse;
import ai.closette.common.ratelimit.RateLimit;
import ai.closette.common.security.SecurityUtil;
import ai.closette.beauty.dto.BeautyAnalyzeResponse;
import ai.closette.beauty.dto.BeautyItemResponse;
import ai.closette.beauty.dto.BeautyProductCandidate;
import ai.closette.beauty.dto.CreateBeautyItemRequest;
import ai.closette.beauty.dto.UpdateBeautyItemRequest;
import ai.closette.beauty.service.BeautyLookupService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/beauty")
public class BeautyController {

    private final BeautyService service;
    private final BeautyLookupService lookup;

    public BeautyController(BeautyService service, BeautyLookupService lookup) {
        this.service = service;
        this.lookup = lookup;
    }

    /** Flow B — look up a product by barcode (Open Beauty Facts). */
    @GetMapping("/lookup")
    public ApiResponse<BeautyProductCandidate> lookup(@RequestParam String barcode) {
        return ApiResponse.ok(lookup.byBarcode(barcode));
    }

    /** Flow B — search products by name (Open Beauty Facts). */
    @GetMapping("/search")
    public ApiResponse<List<BeautyProductCandidate>> search(@RequestParam String q) {
        return ApiResponse.ok(lookup.search(q));
    }

    /** Flow B step 1 — upload a product photo, get an editable AI analysis back. */
    @RateLimit(cost = 9)
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BeautyAnalyzeResponse> analyze(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.analyze(SecurityUtil.currentUserId(), file));
    }

    /** Flow B step 2 — save the confirmed product. */
    @PostMapping("/items")
    public ApiResponse<BeautyItemResponse> create(@Valid @RequestBody CreateBeautyItemRequest request) {
        return ApiResponse.ok(service.create(SecurityUtil.currentUserId(), request));
    }

    @GetMapping("/items")
    public ApiResponse<List<BeautyItemResponse>> list(
            @RequestParam(required = false) BeautyCategory category,
            @RequestParam(required = false) Boolean favorite) {
        return ApiResponse.ok(service.list(SecurityUtil.currentUserId(), category, favorite));
    }

    @GetMapping("/items/{id}")
    public ApiResponse<BeautyItemResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(SecurityUtil.currentUserId(), id));
    }

    /** Edit a saved product (partial update). */
    @PutMapping("/items/{id}")
    public ApiResponse<BeautyItemResponse> update(@PathVariable UUID id,
                                                  @RequestBody UpdateBeautyItemRequest request) {
        return ApiResponse.ok(service.update(SecurityUtil.currentUserId(), id, request));
    }

    @PatchMapping("/items/{id}/favorite")
    public ApiResponse<BeautyItemResponse> toggleFavorite(@PathVariable UUID id) {
        return ApiResponse.ok(service.toggleFavorite(SecurityUtil.currentUserId(), id));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtil.currentUserId(), id);
        return ApiResponse.ok(null);
    }

    /** Read a product's ingredient list from a photo (OCR), returning cleaned ingredient names. */
    @RateLimit(cost = 12)
    @PostMapping(value = "/ingredients/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<String>> scanIngredients(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.scanIngredients(file));
    }

    /** FR-06 — plain-language ingredient explanation. */
    @RateLimit(cost = 1)
    @GetMapping("/ingredients/explain")
    public ApiResponse<IngredientExplanation> explain(@RequestParam String name) {
        return ApiResponse.ok(service.explainIngredient(name));
    }
}
