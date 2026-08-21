package ai.closette.outfit.service;

import ai.closette.ai.dto.OutfitCandidate;
import ai.closette.ai.dto.OutfitSuggestion;
import ai.closette.ai.service.AIService;
import ai.closette.common.exception.ApiException;
import ai.closette.outfit.dto.OutfitDtos.FeedbackRequest;
import ai.closette.outfit.dto.OutfitDtos.GeneratedLook;
import ai.closette.outfit.dto.OutfitDtos.GetReadyRequest;
import ai.closette.outfit.dto.OutfitDtos.OutfitResponse;
import ai.closette.outfit.dto.OutfitDtos.SaveOutfitRequest;
import ai.closette.outfit.model.FeedbackSignal;
import ai.closette.outfit.model.Outfit;
import ai.closette.outfit.model.OutfitFeedback;
import ai.closette.outfit.model.OutfitStatus;
import ai.closette.outfit.repository.OutfitFeedbackRepository;
import ai.closette.outfit.repository.OutfitRepository;
import ai.closette.storage.service.StorageService;
import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.model.WardrobeItem;
import ai.closette.wardrobe.repository.WardrobeItemRepository;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Get Ready + saved-look management (FR-07, FR-08, FR-14). The MVP look generator
 * is a simple rule-based composer over the user's own items — no LLM call needed
 * (NFR-14). Later sprints can swap in a smarter generator behind this method.
 */
@Service
public class OutfitService {

    private final OutfitRepository outfitRepository;
    private final OutfitFeedbackRepository feedbackRepository;
    private final WardrobeItemRepository wardrobeRepository;
    private final StorageService storage;
    private final AIService aiService;

    public OutfitService(OutfitRepository outfitRepository,
                         OutfitFeedbackRepository feedbackRepository,
                         WardrobeItemRepository wardrobeRepository,
                         StorageService storage,
                         AIService aiService) {
        this.outfitRepository = outfitRepository;
        this.feedbackRepository = feedbackRepository;
        this.wardrobeRepository = wardrobeRepository;
        this.storage = storage;
        this.aiService = aiService;
    }

    /** FR-07/08 — compose a complete look from the user's own items (RAG: retrieve
     * the wardrobe as context → the stylist LLM picks the outfit; rule-based fallback). */
    @Transactional(readOnly = true)
    public GeneratedLook generate(UUID userId, GetReadyRequest request) {
        List<WardrobeItem> all = wardrobeRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (all.isEmpty()) {
            return new GeneratedLook("Your first look",
                    "Add a few pieces to your wardrobe and I'll put a complete look together for you.",
                    List.of());
        }

        String occasion = occasionOf(request);

        // Retrieve up to 40 owned items as the LLM's context, then let it compose.
        List<WardrobeItem> candidates = all.size() > 40 ? all.subList(0, 40) : all;
        OutfitSuggestion ai = aiService.generateOutfit(occasion, toCandidates(candidates), List.of());
        if (ai != null && ai.itemIds() != null && !ai.itemIds().isEmpty()) {
            Map<String, WardrobeItem> byId = new LinkedHashMap<>();
            for (WardrobeItem i : all) {
                byId.put(i.getId().toString(), i);
            }
            List<WardrobeItem> look = new ArrayList<>();
            for (String id : ai.itemIds()) {
                WardrobeItem it = byId.get(id);
                if (it != null && !look.contains(it)) {
                    look.add(it);
                }
            }
            if (!look.isEmpty()) {
                String title = notBlank(ai.title()) ? ai.title().trim() : "Your look";
                String rationale = notBlank(ai.rationale()) ? ai.rationale().trim()
                        : "A complete look for " + occasion + ".";
                return new GeneratedLook(title, rationale, look.stream().map(this::toResponse).toList());
            }
        }

        // Fallback: deterministic rule-based composer (AI unavailable / empty result).
        return ruleBasedLook(all, occasion);
    }

    private GeneratedLook ruleBasedLook(List<WardrobeItem> all, String occasion) {
        Map<ClothingCategory, List<WardrobeItem>> byCategory = new LinkedHashMap<>();
        for (WardrobeItem item : all) {
            byCategory.computeIfAbsent(item.getCategory(), k -> new ArrayList<>()).add(item);
        }
        List<WardrobeItem> look = new ArrayList<>();
        Optional<WardrobeItem> dress = first(byCategory, ClothingCategory.DRESSES);
        if (dress.isPresent()) {
            look.add(dress.get());
        } else {
            first(byCategory, ClothingCategory.TOPS).ifPresent(look::add);
            first(byCategory, ClothingCategory.BOTTOMS).ifPresent(look::add);
        }
        first(byCategory, ClothingCategory.OUTERWEAR).ifPresent(look::add);
        first(byCategory, ClothingCategory.SHOES).ifPresent(look::add);
        first(byCategory, ClothingCategory.BAGS).ifPresent(look::add);
        first(byCategory, ClothingCategory.JEWELRY).ifPresent(look::add);
        first(byCategory, ClothingCategory.ACCESSORIES).ifPresent(look::add);
        String rationale = "A complete look for " + occasion
                + " built from " + look.size() + " pieces you already own.";
        return new GeneratedLook("Look 1", rationale, look.stream().map(this::toResponse).toList());
    }

    private static String occasionOf(GetReadyRequest request) {
        if (request == null) return "your day";
        if (notBlank(request.occasion())) return request.occasion().trim();
        if (notBlank(request.prompt())) return request.prompt().trim();
        return "your day";
    }

    private static List<OutfitCandidate> toCandidates(List<WardrobeItem> items) {
        List<OutfitCandidate> out = new ArrayList<>();
        for (WardrobeItem i : items) {
            out.add(new OutfitCandidate(
                    i.getId().toString(),
                    i.getName(),
                    i.getCategory().name().toLowerCase(),
                    i.getSubcategory(),
                    i.getColors(), i.getStyles(), i.getSeasons()));
        }
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Transactional
    public OutfitResponse save(UUID userId, SaveOutfitRequest request) {
        Outfit outfit = new Outfit(userId);
        outfit.setTitle(request.title());
        outfit.setOccasion(request.occasion());
        outfit.setItemIds(request.itemIds().stream().map(UUID::toString).toList());
        outfit.setStatus(request.status() != null ? request.status() : OutfitStatus.SAVED);
        return toResponse(outfitRepository.save(outfit));
    }

    @Transactional(readOnly = true)
    public List<OutfitResponse> list(UUID userId, OutfitStatus status, Boolean favorite) {
        List<Outfit> outfits;
        if (Boolean.TRUE.equals(favorite)) {
            outfits = outfitRepository.findByUserIdAndFavoriteTrueOrderByCreatedAtDesc(userId);
        } else if (status != null) {
            outfits = outfitRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        } else {
            outfits = outfitRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return outfits.stream().map(this::toResponse).toList();
    }

    @Transactional
    public OutfitResponse toggleFavorite(UUID userId, UUID id) {
        Outfit outfit = require(userId, id);
        outfit.setFavorite(!outfit.isFavorite());
        return toResponse(outfitRepository.save(outfit));
    }

    @Transactional
    public OutfitResponse markWorn(UUID userId, UUID id) {
        Outfit outfit = require(userId, id);
        outfit.setStatus(OutfitStatus.WORN);
        feedbackRepository.save(new OutfitFeedback(userId, id, FeedbackSignal.WORE));
        return toResponse(outfitRepository.save(outfit));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        outfitRepository.delete(require(userId, id));
    }

    /** FR-09 — record a feedback event to feed the preference profile. */
    @Transactional
    public void feedback(UUID userId, FeedbackRequest request) {
        feedbackRepository.save(new OutfitFeedback(userId, request.outfitId(), request.signal()));
    }

    // ---- helpers ----

    private Outfit require(UUID userId, UUID id) {
        return outfitRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Outfit not found"));
    }

    private Optional<WardrobeItem> first(Map<ClothingCategory, List<WardrobeItem>> byCategory, ClothingCategory c) {
        List<WardrobeItem> list = byCategory.get(c);
        return (list == null || list.isEmpty()) ? Optional.empty() : Optional.of(list.get(0));
    }

    private OutfitResponse toResponse(Outfit outfit) {
        List<WardrobeItemResponse> items = new ArrayList<>();
        for (String rawId : outfit.getItemIds()) {
            try {
                wardrobeRepository.findByIdAndUserId(UUID.fromString(rawId), outfit.getUserId())
                        .ifPresent(i -> items.add(toResponse(i)));
            } catch (IllegalArgumentException ignored) {
                // skip malformed ids
            }
        }
        return OutfitResponse.from(outfit, items);
    }

    private WardrobeItemResponse toResponse(WardrobeItem item) {
        return WardrobeItemResponse.from(item, storage.presignedUrl(storage.wardrobeBucket(), item.getImageKey()));
    }
}
