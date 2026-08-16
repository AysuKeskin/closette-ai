package ai.closette.beauty.controller;

import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.beauty.model.BeautyCategory;
import ai.closette.beauty.service.BeautyService;
import ai.closette.common.api.ApiResponse;
import ai.closette.common.security.SecurityUtil;
import ai.closette.beauty.dto.BeautyAnalyzeResponse;
import ai.closette.beauty.dto.BeautyItemResponse;
import ai.closette.beauty.dto.CreateBeautyItemRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public BeautyController(BeautyService service) {
        this.service = service;
    }

    /** Flow B step 1 — upload a product photo, get an editable AI analysis back. */
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
            @RequestParam(required = false) BeautyCategory category) {
        return ApiResponse.ok(service.list(SecurityUtil.currentUserId(), category));
    }

    @GetMapping("/items/{id}")
    public ApiResponse<BeautyItemResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(SecurityUtil.currentUserId(), id));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtil.currentUserId(), id);
        return ApiResponse.ok(null);
    }

    /** FR-06 — plain-language ingredient explanation. */
    @GetMapping("/ingredients/explain")
    public ApiResponse<IngredientExplanation> explain(@RequestParam String name) {
        return ApiResponse.ok(service.explainIngredient(name));
    }
}
