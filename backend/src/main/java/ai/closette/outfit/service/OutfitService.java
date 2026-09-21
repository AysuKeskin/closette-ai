package ai.closette.outfit.service;

import ai.closette.ai.dto.OutfitCandidate;
import ai.closette.ai.dto.OutfitSuggestion;
import ai.closette.ai.service.AIService;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.MessageKeys;
import ai.closette.common.i18n.Messages;
import ai.closette.common.exception.MessageKeys;
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
import ai.closette.user.repository.StylePreferenceRepository;
import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.model.WardrobeItem;
import ai.closette.wardrobe.repository.WardrobeItemRepository;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Get Ready + saved-look management (FR-07, FR-08, FR-14). The MVP look generator
 * is a simple rule-based composer over the user's own items — no LLM call needed
 * (NFR-14). Later sprints can swap in a smarter generator behind this method.
 */
@Service
public class OutfitService {

    /** How many owned pieces the stylist sees. Bounded to keep the prompt affordable. */
    private static final int STYLIST_CONTEXT_LIMIT = 40;


    private final OutfitRepository outfitRepository;
    private final OutfitFeedbackRepository feedbackRepository;
    private final WardrobeItemRepository wardrobeRepository;
    private final StorageService storage;
    private final AIService aiService;
    private final StylePreferenceRepository stylePreferences;
    private final Messages messages;

    public OutfitService(OutfitRepository outfitRepository,
                         OutfitFeedbackRepository feedbackRepository,
                         WardrobeItemRepository wardrobeRepository,
                         StorageService storage,
                         AIService aiService,
                         StylePreferenceRepository stylePreferences,
                         Messages messages) {
        this.outfitRepository = outfitRepository;
        this.feedbackRepository = feedbackRepository;
        this.wardrobeRepository = wardrobeRepository;
        this.storage = storage;
        this.aiService = aiService;
        this.stylePreferences = stylePreferences;
        this.messages = messages;
    }

    /** FR-07/08 — compose a complete look from the user's own items (RAG: retrieve
     * the wardrobe as context → the stylist LLM picks the outfit; rule-based fallback). */
    @Transactional(readOnly = true)
    public GeneratedLook generate(UUID userId, GetReadyRequest request) {
        List<WardrobeItem> all = wardrobeRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (all.isEmpty()) {
            return new GeneratedLook(
                    messages.get(MessageKeys.OUTFIT_FIRST_LOOK_TITLE),
                    messages.get(MessageKeys.OUTFIT_FIRST_LOOK_RATIONALE),
                    List.of());
        }

        String occasion = occasionOf(request);

        List<WardrobeItem> candidates = shortlist(all);
        OutfitSuggestion ai = aiService.generateOutfit(
                occasion, toCandidates(candidates), preferencesFor(userId), request.excludeItemIds());
        if (ai != null) {
            boolean pickedSomething = ai.itemIds() != null && !ai.itemIds().isEmpty();
            Map<String, WardrobeItem> byId = new LinkedHashMap<>();
            for (WardrobeItem i : all) {
                byId.put(i.getId().toString(), i);
            }
            List<WardrobeItem> look = new ArrayList<>();
            if (ai.itemIds() != null) {
                for (String id : ai.itemIds()) {
                    WardrobeItem it = byId.get(id);
                    if (it != null && !look.contains(it)) {
                        look.add(it);
                    }
                }
            }
            look = wearable(look, ai.formality(), ai.season(), all);
            if (hasMainGarment(look)) {
                String title = notBlank(ai.title()) ? ai.title().trim()
                        : messages.get(MessageKeys.OUTFIT_DEFAULT_TITLE);
                String rationale = notBlank(ai.rationale()) ? ai.rationale().trim()
                        : messages.get(MessageKeys.OUTFIT_AI_FALLBACK_RATIONALE, occasion);
                return new GeneratedLook(title, rationale, look.stream().map(this::toResponse).toList());
            }
            // The AI deliberately picked nothing (unclear/gibberish occasion): don't fabricate a look.
            if (!pickedSomething) {
                String rationale = notBlank(ai.rationale()) ? ai.rationale().trim()
                        : messages.get(MessageKeys.OUTFIT_UNCLEAR_OCCASION);
                String title = notBlank(ai.title()) ? ai.title().trim()
                        : messages.get(MessageKeys.OUTFIT_RETRY_TITLE);
                return new GeneratedLook(title, rationale, List.of());
            }
            // It picked items but none resolved (hallucinated ids): fall through to the rule-based composer.
        }

        // AI unavailable, or it returned only unusable ids: best-effort deterministic composer.
        // Same gown rule as above — otherwise dropping it from the model's pick would
        // just hand it back through this door.
        boolean blackTie = ai != null && "formal".equalsIgnoreCase(ai.formality());
        List<WardrobeItem> wearableStock = blackTie
                ? all
                : all.stream().filter(i -> !isGown(i)).toList();
        return ruleBasedLook(wearableStock.isEmpty() ? all : wearableStock, occasion);
    }

    /**
     * Enforces what a wearable outfit is, on the way back from the model.
     *
     * The prompt states these rules, but a prompt is a request and this is a
     * guarantee: nobody wears a dress over trousers, or two pairs of shoes. Kept
     * as a repair rather than a rejection, so a mostly-good look still reaches the
     * user instead of falling back to the rule-based composer.
     */
    private static List<WardrobeItem> wearable(List<WardrobeItem> look, String formality,
                                               String season, List<WardrobeItem> wardrobe) {
        boolean blackTie = "formal".equalsIgnoreCase(formality);
        List<WardrobeItem> out = new ArrayList<>();
        boolean hasDress = look.stream().anyMatch(i -> i.getCategory() == ClothingCategory.DRESSES);
        Set<ClothingCategory> used = new HashSet<>();
        for (WardrobeItem item : look) {
            ClothingCategory category = item.getCategory();
            if (hasDress && (category == ClothingCategory.TOPS || category == ClothingCategory.BOTTOMS)) {
                continue;
            }
            if (!blackTie && isGown(item)) {
                continue;
            }
            if (used.add(category)) {
                out.add(item);
            }
        }
        if (!out.isEmpty() && !used.contains(ClothingCategory.SHOES)) {
            List<WardrobeItem> shoes = wardrobe.stream()
                    .filter(i -> i.getCategory() == ClothingCategory.SHOES)
                    .filter(i -> blackTie || !isGown(i))
                    .toList();
            // Only a pair that positively suits the occasion. Falling back to any pair
            // at all is how boots ended up on a beach day: an incomplete look is a
            // smaller failure than an absurd one, and the stylist usually picks shoes
            // itself — this is a safety net, not the main path.
            shoes.stream()
                    .filter(i -> suitsFormality(i, formality))
                    .filter(i -> suitsSeason(i, season))
                    .findFirst()
                    .ifPresent(out::add);
        }
        return out;
    }

    /**
     * Whether anything is actually being worn, rather than accessorised.
     *
     * Stripping a gown out of a too-casual occasion can leave nothing but the shoes,
     * and a pair of heels on its own is not a look. When that happens the rule-based
     * composer builds something wearable instead.
     */
    private static boolean hasMainGarment(List<WardrobeItem> look) {
        return look.stream().anyMatch(i -> i.getCategory() == ClothingCategory.DRESSES
                || i.getCategory() == ClothingCategory.TOPS
                || i.getCategory() == ClothingCategory.BOTTOMS);
    }

    /** Style words that read as dressed-up, and the ones that read as relaxed. */
    private static final Set<String> DRESSY =
            Set.of("elegant", "classic", "timeless", "chic", "formal", "minimal", "romantic");
    private static final Set<String> RELAXED =
            Set.of("casual", "sporty", "boho", "streetwear", "edgy");

    /**
     * Whether the piece belongs in the season the occasion falls in.
     *
     * Formality alone cannot separate these: an edgy leather boot is a perfectly
     * good casual shoe, and still the wrong thing to hand someone for a beach day.
     * A piece with no seasons recorded is allowed through rather than excluded —
     * absent data is not evidence against it.
     */
    private static boolean suitsSeason(WardrobeItem item, String season) {
        if (season == null || season.isBlank()
                || item.getSeasons() == null || item.getSeasons().isEmpty()) {
            return true;
        }
        return item.getSeasons().stream().anyMatch(s -> s.equalsIgnoreCase(season));
    }

    private static boolean suitsFormality(WardrobeItem item, String formality) {
        if (item.getStyles() == null || item.getStyles().isEmpty()) {
            return false;
        }
        Set<String> wanted = "casual".equalsIgnoreCase(formality) ? RELAXED : DRESSY;
        return item.getStyles().stream().anyMatch(st -> wanted.contains(st.toLowerCase(Locale.ROOT)));
    }

    /**
     * An evening gown, by the style word it was catalogued with.
     *
     * The stylist is told to keep these for genuinely special events and mostly does,
     * but "mostly" is how an abiye ends up suggested for a weeknight dinner. Whether
     * the occasion is special enough is the model's call; keeping the gown out of
     * everything else is ours.
     */
    private static boolean isGown(WardrobeItem item) {
        return item.getStyles() != null && item.getStyles().stream().anyMatch("formal"::equalsIgnoreCase);
    }

    /**
     * The wardrobe the stylist gets to see, capped so the prompt stays affordable.
     *
     * Taking the newest N is what a cap must not do: past the cap a user's older
     * pieces become invisible, and the same recent handful comes back every time.
     * Dealing round-robin by category instead keeps every category represented, so
     * there are always shoes and a coat to reach for, and the cut falls on the
     * oldest of an over-represented category rather than on whole categories.
     */
    private static List<WardrobeItem> shortlist(List<WardrobeItem> all) {
        if (all.size() <= STYLIST_CONTEXT_LIMIT) {
            return all;
        }
        Map<ClothingCategory, List<WardrobeItem>> byCategory = new LinkedHashMap<>();
        for (WardrobeItem item : all) {
            byCategory.computeIfAbsent(item.getCategory(), c -> new ArrayList<>()).add(item);
        }
        List<WardrobeItem> picked = new ArrayList<>(STYLIST_CONTEXT_LIMIT);
        int round = 0;
        while (picked.size() < STYLIST_CONTEXT_LIMIT) {
            boolean tookAny = false;
            for (List<WardrobeItem> items : byCategory.values()) {
                if (round < items.size() && picked.size() < STYLIST_CONTEXT_LIMIT) {
                    picked.add(items.get(round));
                    tookAny = true;
                }
            }
            if (!tookAny) {
                break;
            }
            round++;
        }
        return picked;
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
        String rationale = messages.get(MessageKeys.OUTFIT_RULE_BASED_RATIONALE, occasion, look.size());
        return new GeneratedLook(messages.get(MessageKeys.OUTFIT_RULE_BASED_TITLE), rationale,
                look.stream().map(this::toResponse).toList());
    }

    /** The user's style words + favourite colours, fed to the stylist as soft guidance. */
    private List<String> preferencesFor(UUID userId) {
        return stylePreferences.findByUserId(userId)
                .map(p -> {
                    List<String> out = new ArrayList<>();
                    if (p.getPreferredStyles() != null) out.addAll(p.getPreferredStyles());
                    if (p.getFavoriteColors() != null) out.addAll(p.getFavoriteColors());
                    if (p.getColorSeason() != null && !p.getColorSeason().isBlank()) {
                        out.add(messages.get(MessageKeys.OUTFIT_COLOR_SEASON_HINT, p.getColorSeason()));
                    }
                    return out;
                })
                .orElseGet(List::of);
    }

    private String occasionOf(GetReadyRequest request) {
        if (request == null) return messages.get(MessageKeys.OUTFIT_DEFAULT_OCCASION);
        if (notBlank(request.occasion())) return request.occasion().trim();
        if (notBlank(request.prompt())) return request.prompt().trim();
        return messages.get(MessageKeys.OUTFIT_DEFAULT_OCCASION);
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
        outfit.setRationale(request.rationale());
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
                .orElseThrow(() -> ApiException.notFound(MessageKeys.OUTFIT_NOT_FOUND));
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
