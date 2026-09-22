package ai.closette.beauty.service;

import ai.closette.ai.service.AIService;
import ai.closette.ai.dto.BeautyAnalysis;
import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.beauty.dto.BeautyAnalyzeResponse;
import ai.closette.beauty.dto.BeautyItemResponse;
import ai.closette.beauty.dto.CreateBeautyItemRequest;
import ai.closette.beauty.dto.UpdateBeautyItemRequest;
import ai.closette.beauty.model.BeautyCategory;
import ai.closette.beauty.model.BeautyItem;
import ai.closette.beauty.model.IngredientExplanationEntity;
import ai.closette.beauty.repository.BeautyItemRepository;
import ai.closette.beauty.repository.IngredientExplanationRepository;
import ai.closette.common.i18n.Messages;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.common.exception.ErrorCode;
import ai.closette.storage.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BeautyService {

    private final BeautyItemRepository repository;
    private final StorageService storage;
    private final AIService aiService;
    private final IngredientExplanationRepository explanations;

    public BeautyService(BeautyItemRepository repository, StorageService storage, AIService aiService,
                         IngredientExplanationRepository explanations) {
        this.repository = repository;
        this.storage = storage;
        this.aiService = aiService;
        this.explanations = explanations;
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
        ai.closette.storage.service.ImageRegistry.requireOwnedKey(userId, req.imageKey());
        storage.claim(storage.beautyBucket(), userId, req.imageKey());
        BeautyItem item = new BeautyItem(userId);
        item.setBrand(trimToNull(req.brand()));
        item.setProductName(req.productName().trim());
        item.setCategory(req.category());
        item.setImageKey(trimToNull(req.imageKey()));
        item.setImageUrl(trimToNull(req.imageUrl()));
        item.setSize(trimToNull(req.size()));
        if (req.ingredients() != null) item.setIngredients(cleanIngredients(req.ingredients()));
        item.setPurchaseDate(req.purchaseDate());
        item.setOpenedDate(req.openedDate());
        item.setExpiryDate(req.expiryDate());
        item.setPaoMonths(req.paoMonths());
        item.setAmountRemaining(req.amountRemaining());
        item.setFavorite(Boolean.TRUE.equals(req.favorite()));
        return toResponse(repository.save(item));
    }

    @Transactional(readOnly = true)
    public List<BeautyItemResponse> list(UUID userId, BeautyCategory category, Boolean favoritesOnly) {
        boolean onlyFavorites = Boolean.TRUE.equals(favoritesOnly);
        List<BeautyItem> items;
        if (category == null) {
            items = onlyFavorites
                    ? repository.findByUserIdAndFavoriteTrueOrderByCreatedAtDesc(userId)
                    : repository.findByUserIdOrderByCreatedAtDesc(userId);
        } else {
            items = onlyFavorites
                    ? repository.findByUserIdAndCategoryAndFavoriteTrueOrderByCreatedAtDesc(userId, category)
                    : repository.findByUserIdAndCategoryOrderByCreatedAtDesc(userId, category);
        }
        return items.stream().map(this::toResponse).toList();
    }

    /** Favourite / un-favourite in one tap, mirroring the wardrobe. */
    @Transactional
    public BeautyItemResponse toggleFavorite(UUID userId, UUID id) {
        BeautyItem item = require(userId, id);
        item.setFavorite(!item.isFavorite());
        return toResponse(repository.save(item));
    }

    @Transactional
    public BeautyItemResponse update(UUID userId, UUID id, UpdateBeautyItemRequest req) {
        BeautyItem item = require(userId, id);
        if (req.brand() != null) item.setBrand(trimToNull(req.brand()));
        if (req.productName() != null && !req.productName().isBlank()) item.setProductName(req.productName().trim());
        if (req.category() != null) item.setCategory(req.category());
        if (req.size() != null) item.setSize(trimToNull(req.size()));
        if (req.ingredients() != null) item.setIngredients(cleanIngredients(req.ingredients()));
        if (req.favorite() != null) item.setFavorite(req.favorite());
        return toResponse(repository.save(item));
    }

    /** OCR a photo of an ingredient list into cleaned ingredient names (best-effort; empty on failure). */
    public List<String> scanIngredients(MultipartFile file) {
        byte[] bytes = readBytes(file);
        List<String> raw = aiService.extractIngredients(bytes, file.getOriginalFilename(), file.getContentType());
        return cleanIngredients(raw);
    }

    @Transactional(readOnly = true)
    public BeautyItemResponse get(UUID userId, UUID id) {
        return toResponse(require(userId, id));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        BeautyItem item = require(userId, id);
        storage.release(storage.beautyBucket(), userId, item.getImageKey());
        repository.delete(item);
    }

    /**
     * FR-06: explain an ingredient in plain language (via the AI seam).
     *
     * Answered from the shared cache when it can be. "Niacinamide" means the same
     * thing to everyone, so the model is asked once per ingredient per language
     * rather than once per tap; at a cost per call, that is the difference between
     * a fixed bill and one that grows with every reader.
     */
    @Transactional
    public IngredientExplanation explainIngredient(String name) {
        String cleaned = name == null ? "" : name.trim();
        // Guard against junk tokens (a stray ".", a number) reaching the model, which otherwise
        // replies with conversational filler ("sure, give me an ingredient") shown to the user.
        if (cleaned.length() < 2 || !cleaned.matches(".*\\p{L}{2,}.*")) {
            throw ApiException.validation(MessageKeys.INGREDIENT_REQUIRED);
        }
        String lang = Messages.currentLanguageTag();
        var cached = explanations.findByInciNameIgnoreCaseAndLang(cleaned, lang);
        if (cached.isPresent()) {
            return new IngredientExplanation(cleaned, cached.get().getExplanation());
        }
        IngredientExplanation fresh = aiService.explainIngredient(cleaned);
        if (fresh != null && fresh.explanation() != null && !fresh.explanation().isBlank()) {
            explanations.save(new IngredientExplanationEntity(cleaned, lang, fresh.explanation()));
        }
        return fresh;
    }

    private BeautyItem require(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound(MessageKeys.BEAUTY_NOT_FOUND));
    }

    private BeautyItemResponse toResponse(BeautyItem item) {
        // Uploaded photos live in MinIO (presigned); search-sourced products keep an external URL.
        String displayUrl = item.getImageKey() != null
                ? storage.presignedOwnedUrl(storage.beautyBucket(), item.getUserId(), item.getImageKey())
                : item.getImageUrl();
        return BeautyItemResponse.from(item, displayUrl);
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static final Set<String> INGREDIENT_NOISE = Set.of(
            "and", "or", "with", "may", "contain", "may contain", "other", "ingredients", "ingredient",
            "n/a", "na", "none", "ci", "and/or", "plus", "minus");

    /** Drop connector words and stray punctuation so ingredient chips are real ingredients, not "." or "and". */
    private static List<String> cleanIngredients(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String r : raw) {
            if (r == null) continue;
            String t = r.strip()
                    .replaceAll("^[\\s.;:()\\[\\]/*+_\\-]+", "")
                    .replaceAll("[\\s.;:()\\[\\]/*+_\\-]+$", "")
                    .strip();
            if (t.isBlank() || t.length() >= 60) continue;
            if (!t.matches(".*\\p{L}{2,}.*")) continue;
            if (INGREDIENT_NOISE.contains(t.toLowerCase(Locale.ROOT))) continue;
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation(MessageKeys.IMAGE_REQUIRED);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation(MessageKeys.IMAGE_UNREADABLE);
        }
    }
}
