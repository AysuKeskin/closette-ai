package ai.closette.wardrobe.service;

import ai.closette.ai.service.AIService;
import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.common.exception.ApiException;
import ai.closette.storage.service.StorageService;
import ai.closette.wardrobe.dto.AnalyzeResponse;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.dto.UpdateItemRequest;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.WardrobeFilter;
import ai.closette.wardrobe.model.WardrobeItem;
import ai.closette.wardrobe.repository.WardrobeItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WardrobeService {

    private final WardrobeItemRepository repository;
    private final StorageService storage;
    private final AIService aiService;

    public WardrobeService(WardrobeItemRepository repository, StorageService storage, AIService aiService) {
        this.repository = repository;
        this.storage = storage;
        this.aiService = aiService;
    }

    /** Flow A step 1: store the photo and run AI analysis (editable by the user). */
    @Transactional
    public AnalyzeResponse analyze(UUID userId, MultipartFile file) {
        byte[] bytes = readBytes(file);
        String key = storage.upload(storage.wardrobeBucket(), userId, bytes,
                file.getContentType(), file.getOriginalFilename());
        ClothingAnalysis analysis = aiService.analyzeClothing(bytes, file.getOriginalFilename(), file.getContentType());
        return new AnalyzeResponse(key, storage.presignedUrl(storage.wardrobeBucket(), key), analysis);
    }

    /** Flow A step 2: persist the confirmed item. */
    @Transactional
    public WardrobeItemResponse create(UUID userId, CreateItemRequest req) {
        WardrobeItem item = new WardrobeItem(userId);
        item.setName(req.name().trim());
        item.setCategory(req.category());
        item.setSubcategory(trimToNull(req.subcategory()));
        item.setColors(nullToEmpty(req.colors()));
        item.setPattern(trimToNull(req.pattern()));
        item.setStyles(nullToEmpty(req.styles()));
        item.setSeasons(nullToEmpty(req.seasons()));
        item.setBrand(trimToNull(req.brand()));
        item.setSize(trimToNull(req.size()));
        item.setImageKey(trimToNull(req.imageKey()));
        item.setFavorite(Boolean.TRUE.equals(req.favorite()));
        return toResponse(repository.save(item));
    }

    @Transactional(readOnly = true)
    public List<WardrobeItemResponse> list(UUID userId, WardrobeFilter filter) {
        List<WardrobeItem> items = repository.findAll(baseSpec(userId, filter));
        return items.stream()
                .filter(i -> matchesColor(i, filter.color()))
                .filter(i -> matchesSeason(i, filter.season()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WardrobeItemResponse> recent(UUID userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(Math.max(1, limit))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WardrobeItemResponse get(UUID userId, UUID id) {
        return toResponse(require(userId, id));
    }

    @Transactional
    public WardrobeItemResponse update(UUID userId, UUID id, UpdateItemRequest req) {
        WardrobeItem item = require(userId, id);
        if (req.name() != null && !req.name().isBlank()) item.setName(req.name().trim());
        if (req.category() != null) item.setCategory(req.category());
        if (req.subcategory() != null) item.setSubcategory(trimToNull(req.subcategory()));
        if (req.colors() != null) item.setColors(req.colors());
        if (req.pattern() != null) item.setPattern(trimToNull(req.pattern()));
        if (req.styles() != null) item.setStyles(req.styles());
        if (req.seasons() != null) item.setSeasons(req.seasons());
        if (req.brand() != null) item.setBrand(trimToNull(req.brand()));
        if (req.size() != null) item.setSize(trimToNull(req.size()));
        if (req.favorite() != null) item.setFavorite(req.favorite());
        return toResponse(repository.save(item));
    }

    @Transactional
    public WardrobeItemResponse toggleFavorite(UUID userId, UUID id) {
        WardrobeItem item = require(userId, id);
        item.setFavorite(!item.isFavorite());
        return toResponse(repository.save(item));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(require(userId, id));
    }

    // ---- helpers ----

    private WardrobeItem require(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Item not found"));
    }

    private WardrobeItemResponse toResponse(WardrobeItem item) {
        String url = storage.presignedUrl(storage.wardrobeBucket(), item.getImageKey());
        return WardrobeItemResponse.from(item, url);
    }

    private Specification<WardrobeItem> baseSpec(UUID userId, WardrobeFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (f.category() != null) {
                predicates.add(cb.equal(root.get("category"), f.category()));
            }
            if (f.brand() != null && !f.brand().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), f.brand().toLowerCase()));
            }
            if (f.favorite() != null) {
                predicates.add(cb.equal(root.get("favorite"), f.favorite()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + f.q().toLowerCase() + "%"));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean matchesColor(WardrobeItem item, String color) {
        if (color == null || color.isBlank()) return true;
        return item.getColors().stream().anyMatch(c -> c.equalsIgnoreCase(color));
    }

    private boolean matchesSeason(WardrobeItem item, String season) {
        if (season == null || season.isBlank()) return true;
        return item.getSeasons().stream().anyMatch(s -> s.equalsIgnoreCase(season));
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    ai.closette.common.exception.ErrorCode.VALIDATION, "An image file is required");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.storage("Could not read the uploaded image");
        }
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static List<String> nullToEmpty(List<String> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }
}
