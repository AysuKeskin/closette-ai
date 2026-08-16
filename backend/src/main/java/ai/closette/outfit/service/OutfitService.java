package ai.closette.outfit.service;

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

    public OutfitService(OutfitRepository outfitRepository,
                         OutfitFeedbackRepository feedbackRepository,
                         WardrobeItemRepository wardrobeRepository,
                         StorageService storage) {
        this.outfitRepository = outfitRepository;
        this.feedbackRepository = feedbackRepository;
        this.wardrobeRepository = wardrobeRepository;
        this.storage = storage;
    }

    /** FR-07/08 — compose a complete look from the user's own items. */
    @Transactional(readOnly = true)
    public GeneratedLook generate(UUID userId, GetReadyRequest request) {
        List<WardrobeItem> all = wardrobeRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (all.isEmpty()) {
            return new GeneratedLook("Your first look",
                    "Add a few pieces to your wardrobe and I'll put a complete look together for you.",
                    List.of());
        }

        Map<ClothingCategory, List<WardrobeItem>> byCategory = new LinkedHashMap<>();
        for (WardrobeItem item : all) {
            byCategory.computeIfAbsent(item.getCategory(), k -> new ArrayList<>()).add(item);
        }

        List<WardrobeItem> look = new ArrayList<>();
        // Base: a dress, or a top + bottom.
        Optional<WardrobeItem> dress = first(byCategory, ClothingCategory.DRESSES);
        if (dress.isPresent()) {
            look.add(dress.get());
        } else {
            first(byCategory, ClothingCategory.TOPS).ifPresent(look::add);
            first(byCategory, ClothingCategory.BOTTOMS).ifPresent(look::add);
        }
        // Complete the look (FR-08).
        first(byCategory, ClothingCategory.OUTERWEAR).ifPresent(look::add);
        first(byCategory, ClothingCategory.SHOES).ifPresent(look::add);
        first(byCategory, ClothingCategory.BAGS).ifPresent(look::add);
        first(byCategory, ClothingCategory.JEWELRY).ifPresent(look::add);
        first(byCategory, ClothingCategory.ACCESSORIES).ifPresent(look::add);

        String occasion = request != null && request.occasion() != null && !request.occasion().isBlank()
                ? request.occasion()
                : (request != null && request.prompt() != null ? request.prompt() : "your day");
        String rationale = "A complete look for " + occasion.trim()
                + " built from " + look.size() + " pieces you already own.";

        return new GeneratedLook("Look 1", rationale, look.stream().map(this::toResponse).toList());
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
