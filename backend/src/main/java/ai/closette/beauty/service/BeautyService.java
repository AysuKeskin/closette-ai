package ai.closette.beauty.service;

import ai.closette.ai.service.AIService;
import ai.closette.ai.dto.BeautyAnalysis;
import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.beauty.dto.BeautyAnalyzeResponse;
import ai.closette.beauty.dto.BeautyItemResponse;
import ai.closette.beauty.dto.CreateBeautyItemRequest;
import ai.closette.beauty.model.BeautyCategory;
import ai.closette.beauty.model.BeautyItem;
import ai.closette.beauty.repository.BeautyItemRepository;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.storage.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class BeautyService {

    private final BeautyItemRepository repository;
    private final StorageService storage;
    private final AIService aiService;

    public BeautyService(BeautyItemRepository repository, StorageService storage, AIService aiService) {
        this.repository = repository;
        this.storage = storage;
        this.aiService = aiService;
    }

    /** Flow B step 1 — upload a product photo, get an editable AI analysis back. */
    public BeautyAnalyzeResponse analyze(UUID userId, MultipartFile file) {
        byte[] bytes = readBytes(file);
        String key = storage.upload(storage.beautyBucket(), userId, bytes,
                file.getContentType(), file.getOriginalFilename());
        BeautyAnalysis analysis = aiService.analyzeBeautyPhoto(bytes, file.getOriginalFilename(), file.getContentType());
        return new BeautyAnalyzeResponse(key, storage.presignedUrl(storage.beautyBucket(), key), analysis);
    }

    @Transactional
    public BeautyItemResponse create(UUID userId, CreateBeautyItemRequest req) {
        BeautyItem item = new BeautyItem(userId);
        item.setBrand(trimToNull(req.brand()));
        item.setProductName(req.productName().trim());
        item.setCategory(req.category());
        item.setImageKey(trimToNull(req.imageKey()));
        item.setSize(trimToNull(req.size()));
        if (req.ingredients() != null) item.setIngredients(req.ingredients());
        item.setPurchaseDate(req.purchaseDate());
        item.setOpenedDate(req.openedDate());
        item.setExpiryDate(req.expiryDate());
        item.setPaoMonths(req.paoMonths());
        item.setAmountRemaining(req.amountRemaining());
        item.setFavorite(Boolean.TRUE.equals(req.favorite()));
        return toResponse(repository.save(item));
    }

    @Transactional(readOnly = true)
    public List<BeautyItemResponse> list(UUID userId, BeautyCategory category) {
        List<BeautyItem> items = (category == null)
                ? repository.findByUserIdOrderByCreatedAtDesc(userId)
                : repository.findByUserIdAndCategoryOrderByCreatedAtDesc(userId, category);
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BeautyItemResponse get(UUID userId, UUID id) {
        return toResponse(require(userId, id));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(require(userId, id));
    }

    /** FR-06: explain an ingredient in plain language (via the AI seam). */
    public IngredientExplanation explainIngredient(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    ai.closette.common.exception.ErrorCode.VALIDATION, "Ingredient name is required");
        }
        return aiService.explainIngredient(name.trim());
    }

    private BeautyItem require(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Beauty item not found"));
    }

    private BeautyItemResponse toResponse(BeautyItem item) {
        return BeautyItemResponse.from(item, storage.presignedUrl(storage.beautyBucket(), item.getImageKey()));
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "An image is required");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "Could not read the image");
        }
    }
}
