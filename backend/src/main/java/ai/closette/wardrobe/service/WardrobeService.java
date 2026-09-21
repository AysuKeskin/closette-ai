package ai.closette.wardrobe.service;

import ai.closette.ai.service.AIService;
import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.storage.service.StorageService;
import ai.closette.wardrobe.dto.AnalyzeResponse;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.dto.RetagSummary;
import ai.closette.wardrobe.dto.UpdateItemRequest;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.WardrobeFilter;
import ai.closette.wardrobe.model.WardrobeItem;
import ai.closette.wardrobe.repository.EmbeddingRepository;
import ai.closette.wardrobe.repository.WardrobeItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(WardrobeService.class);

    /** Pieces re-catalogued per run. Bounded so one tap cannot spend a day's AI budget. */
    private static final int RETAG_BATCH = 40;

    private final WardrobeItemRepository repository;
    private final StorageService storage;
    private final ai.closette.auth.service.AiConsentService consent;
    private final AIService aiService;
    private final EmbeddingRepository embeddings;

    public WardrobeService(WardrobeItemRepository repository, StorageService storage,
                           AIService aiService, EmbeddingRepository embeddings, ai.closette.auth.service.AiConsentService consent) {
        this.repository = repository;
        this.storage = storage;
        this.consent = consent;
        this.aiService = aiService;
        this.embeddings = embeddings;
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

    /**
     * Re-derives the catalogue tags of pieces already in the wardrobe.
     *
     * The tags decide what the stylist can reason about: an evening gown filed as a
     * summer dress gets suggested for coffee, and a piece with no seasons cannot be
     * kept out of the snow. Items catalogued before the vocabulary was pinned down
     * carry exactly those gaps, and there is no way to fix them one by one.
     *
     * Derived from the name the user gave the piece rather than its photo: the text
     * call costs a fraction of a vision call, which matters when the whole wardrobe
     * goes through it, and the name is what the user themselves called it.
     *
     * Capped per run, oldest-touched first, so repeated runs walk the wardrobe. Only
     * the AI-derived tags are touched — never the name, brand, size, colours or
     * favourite, which are the user's own.
     */
    @Transactional
    public RetagSummary retag(UUID userId) {
        List<WardrobeItem> items = repository.findByUserIdOrderByUpdatedAtAsc(userId);
        int total = items.size();
        List<WardrobeItem> batch = items.size() > RETAG_BATCH ? items.subList(0, RETAG_BATCH) : items;

        int updated = 0;
        int failed = 0;
        for (WardrobeItem item : batch) {
            String description = describe(item);
            if (description.isBlank()) {
                continue;
            }
            ClothingAnalysis analysis = aiService.parseClothingText(description);
            // Best-effort: the AI seam returns null when the service is down, and one
            // unparseable name must not abandon the rest of the wardrobe.
            if (analysis == null || "unknown".equalsIgnoreCase(analysis.category())) {
                failed++;
                log.warn("Re-catalogue produced nothing for item {} ({})", item.getId(), description);
                continue;
            }
            if (applyTags(item, analysis)) {
                updated++;
            }
        }
        repository.saveAll(batch);
        int remaining = Math.max(0, total - batch.size());
        log.info("Re-catalogued {} of {} items for user {} ({} failed, {} left)",
                updated, batch.size(), userId, failed, remaining);
        return new RetagSummary(batch.size(), updated, failed, remaining);
    }

    /** What the piece is, in the user's own words. */
    private static String describe(WardrobeItem item) {
        StringBuilder text = new StringBuilder();
        if (item.getName() != null) text.append(item.getName()).append(' ');
        if (item.getSubcategory() != null) text.append(item.getSubcategory()).append(' ');
        if (item.getColors() != null) text.append(String.join(" ", item.getColors()));
        return text.toString().trim();
    }

    /** Overwrites only what the AI derives; returns whether anything actually moved. */
    private static boolean applyTags(WardrobeItem item, ClothingAnalysis analysis) {
        boolean changed = false;
        if (analysis.styles() != null && !analysis.styles().isEmpty()
                && !analysis.styles().equals(item.getStyles())) {
            item.setStyles(analysis.styles());
            changed = true;
        }
        if (analysis.seasons() != null && !analysis.seasons().isEmpty()
                && !analysis.seasons().equals(item.getSeasons())) {
            item.setSeasons(analysis.seasons());
            changed = true;
        }
        // Pattern is filled in only when the piece has none. Unlike styles and seasons,
        // it cannot be read off a name: asked about "Mavi gömlek" the parser answers
        // "solid" whether or not the shirt is striped, and overwriting would replace
        // what the photo actually showed with that guess.
        if (item.getPattern() == null || item.getPattern().isBlank()) {
            String pattern = trimToNull(analysis.pattern());
            if (pattern != null) {
                item.setPattern(pattern);
                changed = true;
            }
        }
        return changed;
    }

    /** Flow A step 2: persist the confirmed item. */
    @Transactional
    public WardrobeItemResponse create(UUID userId, CreateItemRequest req) {
        ai.closette.storage.service.ImageRegistry.requireOwnedKey(userId, req.imageKey());
        storage.claim(storage.wardrobeBucket(), userId, req.imageKey());
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
        // Flush so the row exists before the JDBC embedding UPDATE hits the same row.
        WardrobeItem saved = repository.saveAndFlush(item);
        computeEmbedding(saved);
        return toResponse(saved);
    }

    /** Best-effort visual embedding (pgvector). Runs once per item; never blocks save. */
    private void computeEmbedding(WardrobeItem item) {
        if (item.getImageKey() == null || item.getImageKey().isBlank()) {
            return;
        }
        try {
            byte[] bytes = storage.download(storage.wardrobeBucket(), item.getImageKey());
            if (bytes == null) return;
            consent.require(item.getUserId());
            float[] vector = aiService.embedItem(bytes, item.getImageKey(), "image/jpeg");
            if (vector != null && vector.length > 0) {
                embeddings.saveWardrobeEmbedding(item.getId(), vector);
            }
        } catch (Exception e) {
            // Similarity is a nice-to-have; a failure here must not break item creation.
            log.warn("Embedding failed for item {}", item.getId(), e);
        }
    }

    /** Owned items most visually similar to the given one (pgvector cosine). */
    @Transactional(readOnly = true)
    public List<WardrobeItemResponse> similar(UUID userId, UUID itemId, int limit) {
        require(userId, itemId); // 404 if not the user's item
        List<UUID> ids = embeddings.similarWardrobe(userId, itemId, limit);
        List<WardrobeItemResponse> out = new ArrayList<>();
        for (UUID id : ids) {
            repository.findByIdAndUserId(id, userId).ifPresent(i -> out.add(toResponse(i)));
        }
        return out;
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
        WardrobeItem item = require(userId, id);
        storage.release(storage.wardrobeBucket(), userId, item.getImageKey());
        repository.delete(item);
    }

    // ---- helpers ----

    private WardrobeItem require(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound(MessageKeys.ITEM_NOT_FOUND));
    }

    private WardrobeItemResponse toResponse(WardrobeItem item) {
        String url = storage.presignedOwnedUrl(storage.wardrobeBucket(), item.getUserId(), item.getImageKey());
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
            throw ApiException.validation(MessageKeys.IMAGE_REQUIRED);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation(MessageKeys.IMAGE_UNREADABLE);
        }
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static List<String> nullToEmpty(List<String> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }
}
