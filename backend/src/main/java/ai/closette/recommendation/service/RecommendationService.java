package ai.closette.recommendation.service;

import ai.closette.ai.dto.BuyAdvice;
import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.ai.service.AIService;
import ai.closette.auth.service.AccountGuard;
import ai.closette.common.exception.MessageKeys;
import ai.closette.common.i18n.Messages;
import ai.closette.outfit.model.Outfit;
import ai.closette.outfit.model.OutfitFeedback;
import ai.closette.outfit.repository.OutfitFeedbackRepository;
import ai.closette.outfit.repository.OutfitRepository;
import ai.closette.recommendation.model.PreferenceWeight;
import ai.closette.recommendation.repository.PreferenceWeightRepository;
import ai.closette.recommendation.dto.RecommendationDtos.PreferenceEntry;
import ai.closette.recommendation.dto.RecommendationDtos.ShouldIBuyRequest;
import ai.closette.recommendation.dto.RecommendationDtos.ShouldIBuyResponse;
import ai.closette.storage.service.StorageService;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * FR-09 preference profile + FR-10 "Should I buy this?". Scoring is rule-based and
 * explainable (NFR-07): a fit score from how much the candidate's colours/styles
 * already live in the wardrobe, plus a redundancy check for near-duplicates. The
 * LLM is used only to phrase the final verdict/explanation (RAG), and is best-effort —
 * the rule verdict/explanation stand in whenever the model is unavailable.
 */
@Service
public class RecommendationService {

    private final PreferenceWeightRepository weightRepository;
    private final OutfitFeedbackRepository feedbackRepository;
    private final OutfitRepository outfitRepository;
    private final WardrobeItemRepository wardrobeRepository;
    private final StorageService storage;
    private final AccountGuard accountGuard;
    private final AIService aiService;
    private final Messages messages;

    public RecommendationService(PreferenceWeightRepository weightRepository,
                                 OutfitFeedbackRepository feedbackRepository,
                                 OutfitRepository outfitRepository,
                                 WardrobeItemRepository wardrobeRepository,
                                 StorageService storage,
                                 AccountGuard accountGuard,
                                 AIService aiService,
                                 Messages messages) {
        this.weightRepository = weightRepository;
        this.feedbackRepository = feedbackRepository;
        this.outfitRepository = outfitRepository;
        this.wardrobeRepository = wardrobeRepository;
        this.storage = storage;
        this.accountGuard = accountGuard;
        this.aiService = aiService;
        this.messages = messages;
    }

    /** Recompute and persist the preference profile from accumulated feedback. */
    @Transactional
    public List<PreferenceEntry> recomputeProfile(UUID userId) {
        Map<String, Double> raw = new TreeMap<>();
        for (OutfitFeedback fb : feedbackRepository.findByUserId(userId)) {
            if (fb.getOutfitId() == null) continue;
            Outfit outfit = outfitRepository.findByIdAndUserId(fb.getOutfitId(), userId).orElse(null);
            if (outfit == null) continue;
            int w = fb.getSignal().weight();
            for (String rawId : outfit.getItemIds()) {
                WardrobeItem item = findItem(userId, rawId);
                if (item == null) continue;
                for (String attr : attributesOf(item)) {
                    raw.merge(attr, (double) w, Double::sum);
                }
            }
        }

        double max = raw.values().stream().mapToDouble(Math::abs).max().orElse(1.0);
        if (max <= 0) max = 1.0;

        weightRepository.deleteByUserId(userId);
        List<PreferenceWeight> saved = new ArrayList<>();
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            double normalized = Math.max(0.0, e.getValue() / max);
            saved.add(new PreferenceWeight(userId, e.getKey(), round2(normalized)));
        }
        weightRepository.saveAll(saved);

        return weightRepository.findByUserIdOrderByWeightDesc(userId).stream()
                .map(p -> new PreferenceEntry(p.getAttribute(), p.getWeight()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PreferenceEntry> getProfile(UUID userId) {
        return weightRepository.findByUserIdOrderByWeightDesc(userId).stream()
                .map(p -> new PreferenceEntry(p.getAttribute(), p.getWeight()))
                .toList();
    }

    /** FR-10 — natural-language input: the user describes the item, the AI extracts attributes. */
    @Transactional(readOnly = true)
    public ShouldIBuyResponse shouldIBuyFromDescription(UUID userId, String description) {
        accountGuard.requireVerifiedEmail(userId);
        ClothingAnalysis a = aiService.parseClothingText(description);
        return fromAnalysis(userId, a, description);
    }

    /** FR-10 — photo input: the VLM reads the item from the picture, then we compare it. */
    @Transactional(readOnly = true)
    public ShouldIBuyResponse shouldIBuyFromPhoto(UUID userId, byte[] image, String filename, String contentType) {
        accountGuard.requireVerifiedEmail(userId);
        ClothingAnalysis a = aiService.analyzeClothing(image, filename, contentType);
        return fromAnalysis(userId, a, null);
    }

    private ShouldIBuyResponse fromAnalysis(UUID userId, ClothingAnalysis a, String fallbackText) {
        ClothingCategory category = a != null ? mapCategory(a.category()) : null;
        List<String> colors = a != null && a.colors() != null ? a.colors() : List.of();
        List<String> styles = a != null && a.styles() != null ? a.styles() : List.of();

        // The AI understood a photo/description but it isn't a clothing item (gibberish, a question,
        // an unrelated object): don't fabricate a verdict — say we couldn't read it.
        if (a != null && category == null && colors.isEmpty() && styles.isEmpty()) {
            return new ShouldIBuyResponse(0, "maybe", 0, 0, List.of(),
                    messages.get(MessageKeys.BUY_UNREADABLE),
                    buildLabel(a, fallbackText), List.of(), List.of(), false);
        }

        String label = buildLabel(a, fallbackText);
        return evaluate(userId, new ShouldIBuyRequest(category, colors, styles), label);
    }

    /** FR-10 — compare a candidate (already-structured) purchase against the user's wardrobe. */
    @Transactional(readOnly = true)
    public ShouldIBuyResponse shouldIBuy(UUID userId, ShouldIBuyRequest req) {
        accountGuard.requireVerifiedEmail(userId);
        return evaluate(userId, req, null);
    }

    private ShouldIBuyResponse evaluate(UUID userId, ShouldIBuyRequest req, String detectedLabel) {
        List<WardrobeItem> wardrobe = wardrobeRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Set<String> candidateColors = lower(req.colors());
        Set<String> candidateStyles = lower(req.styles());

        // Aggregate what the wardrobe already contains, so "fit" measures the candidate's own
        // attributes against the closet — independent of wardrobe size.
        Set<String> ownedColors = new HashSet<>();
        Set<String> ownedStyles = new HashSet<>();
        for (WardrobeItem item : wardrobe) {
            ownedColors.addAll(lower(item.getColors()));
            ownedStyles.addAll(lower(item.getStyles()));
        }

        List<WardrobeItem> matching = new ArrayList<>();
        List<WardrobeItem> similar = new ArrayList<>();
        Map<ClothingCategory, Integer> matchByCategory = new LinkedHashMap<>();

        for (WardrobeItem item : wardrobe) {
            boolean colorMatch = intersects(lower(item.getColors()), candidateColors);
            boolean styleMatch = intersects(lower(item.getStyles()), candidateStyles);
            if (colorMatch || styleMatch) {
                matching.add(item);
                matchByCategory.merge(item.getCategory(), 1, Integer::sum);
            }
            // A near-duplicate: same category and it shares a colour or a style.
            if (req.category() != null && item.getCategory() == req.category() && (colorMatch || styleMatch)) {
                similar.add(item);
            }
        }

        int total = wardrobe.size();
        // Fit = share of the candidate's own colours/styles that already appear in the wardrobe.
        int candidateAttrs = candidateColors.size() + candidateStyles.size();
        int attrMatched = countPresent(candidateColors, ownedColors) + countPresent(candidateStyles, ownedStyles);
        int score = total == 0 ? 0
                : candidateAttrs == 0 ? 50 // nothing described: neutral, lean on the AI/redundancy signal
                : (int) Math.round(100.0 * attrMatched / candidateAttrs);

        String explanation = buildExplanation(total, score, candidateAttrs, attrMatched,
                matching.size(), similar.size(), matchByCategory, req.category());

        List<WardrobeItemResponse> similarResponses = similar.stream()
                .limit(6)
                .map(i -> WardrobeItemResponse.from(i, storage.presignedUrl(storage.wardrobeBucket(), i.getImageKey())))
                .toList();

        // RAG: retrieved similar owned items + code-computed scores → LLM verdict.
        BuyAdvice advice = aiService.buyAdvice(
                candidateContext(req),
                matchContext(matching),
                Map.of("fitScore", score,
                        "pairsWithCount", matching.size(),
                        "alreadyOwnSimilar", similar.size()));
        String verdict = advice != null && advice.verdict() != null
                ? advice.verdict() : ruleVerdict(score, candidateAttrs, similar.size());
        String finalExplanation = advice != null && advice.explanation() != null && !advice.explanation().isBlank()
                ? advice.explanation() : explanation;

        return new ShouldIBuyResponse(score, verdict, matching.size(), similar.size(), similarResponses,
                finalExplanation, detectedLabel,
                req.colors() == null ? List.of() : req.colors(),
                req.styles() == null ? List.of() : req.styles(),
                true);
    }

    /** Map the AI's singular category word to the wardrobe enum; null if unrecognised. */
    private static ClothingCategory mapCategory(String aiCategory) {
        if (aiCategory == null) return null;
        return switch (aiCategory.trim().toLowerCase(Locale.ROOT)) {
            case "top" -> ClothingCategory.TOPS;
            case "bottom" -> ClothingCategory.BOTTOMS;
            case "dress" -> ClothingCategory.DRESSES;
            case "outerwear" -> ClothingCategory.OUTERWEAR;
            case "shoes" -> ClothingCategory.SHOES;
            case "bag" -> ClothingCategory.BAGS;
            case "jewelry", "jewellery" -> ClothingCategory.JEWELRY;
            case "accessory" -> ClothingCategory.ACCESSORIES;
            default -> null;
        };
    }

    /** A short human label of what we understood, e.g. "beige blazer". */
    private String buildLabel(ClothingAnalysis a, String fallbackText) {
        if (a != null) {
            String noun = a.subcategory() != null && !a.subcategory().isBlank()
                    ? a.subcategory().trim()
                    : (a.category() != null ? a.category() : "item");
            String color = a.colors() != null && !a.colors().isEmpty() ? a.colors().get(0) + " " : "";
            return (color + noun).trim();
        }
        if (fallbackText != null && !fallbackText.isBlank()) {
            String t = fallbackText.trim();
            return t.length() > 60 ? t.substring(0, 60) + "…" : t;
        }
        return messages.get(MessageKeys.BUY_LABEL_FALLBACK);
    }

    private static Map<String, Object> candidateContext(ShouldIBuyRequest req) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("category", req.category() != null ? req.category().name().toLowerCase(Locale.ROOT) : null);
        c.put("colors", req.colors() == null ? List.of() : req.colors());
        c.put("styles", req.styles() == null ? List.of() : req.styles());
        return c;
    }

    private static List<Map<String, Object>> matchContext(List<WardrobeItem> matching) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WardrobeItem it : matching.stream().limit(8).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", it.getName());
            m.put("category", it.getCategory().name().toLowerCase(Locale.ROOT));
            m.put("colors", it.getColors());
            m.put("styles", it.getStyles());
            out.add(m);
        }
        return out;
    }

    /**
     * Rule fallback when the model is unavailable. Two forces: how well it fits (score) and how
     * many near-duplicates are already owned (redundancy). Owning several similar leans skip;
     * a strong fit with none owned leans buy; a weak fit leans skip; everything else is a judgement call.
     */
    private static String ruleVerdict(int score, int candidateAttrs, int similarCount) {
        if (similarCount >= 2) return "skip";
        if (candidateAttrs > 0 && score < 35) return "skip";
        if (score >= 60 && similarCount == 0) return "buy";
        return "maybe";
    }

    private static int countPresent(Set<String> candidate, Set<String> owned) {
        int n = 0;
        for (String c : candidate) if (owned.contains(c)) n++;
        return n;
    }

    // ---- helpers ----

    /**
     * The verdict in plain language (NFR-07).
     *
     * Each sentence is one bundle entry rather than concatenated fragments:
     * English puts the count before the noun and Turkish inflects around it, so
     * only a whole sentence can be translated into something that reads naturally.
     */
    private String buildExplanation(int total, int score, int candidateAttrs, int attrMatched,
                                    int matchCount, int similarCount,
                                    Map<ClothingCategory, Integer> byCategory, ClothingCategory category) {
        if (total == 0) {
            return messages.get(MessageKeys.BUY_WARDROBE_EMPTY);
        }
        List<String> sentences = new ArrayList<>();

        // Lead with how well it fits the user's palette/style.
        if (candidateAttrs == 0) {
            sentences.add(messages.get(MessageKeys.BUY_NO_TAGS));
        } else {
            String fitKey = score >= 70 ? MessageKeys.BUY_FIT_GOOD
                    : score >= 40 ? MessageKeys.BUY_FIT_PARTIAL
                    : MessageKeys.BUY_FIT_LOW;
            sentences.add(messages.get(fitKey, attrMatched, candidateAttrs));
        }

        // How much it pairs with what's already there.
        if (matchCount > 0) {
            List<String> parts = new ArrayList<>();
            byCategory.forEach((cat, count) -> parts.add(count + " " + categoryWord(cat, count)));
            sentences.add(messages.plural(MessageKeys.BUY_PAIRS_WITH, matchCount,
                    matchCount, String.join(", ", parts)));
        }

        // Redundancy nudge.
        if (similarCount > 0) {
            sentences.add(messages.plural(MessageKeys.BUY_ALREADY_OWN, similarCount,
                    similarCount, categoryWord(category, similarCount)));
        }
        return String.join(" ", sentences);
    }

    /** The category as a word inside a sentence, e.g. "tops" / "üst". */
    private String categoryWord(ClothingCategory category, int count) {
        String key = category == null
                ? MessageKeys.CATEGORY_GENERIC
                : MessageKeys.CATEGORY_PREFIX + category.name();
        return messages.plural(key, count);
    }

    private WardrobeItem findItem(UUID userId, String rawId) {
        try {
            return wardrobeRepository.findByIdAndUserId(UUID.fromString(rawId), userId).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Set<String> attributesOf(WardrobeItem item) {
        Set<String> attrs = new HashSet<>();
        attrs.addAll(lower(item.getColors()));
        attrs.addAll(lower(item.getStyles()));
        return attrs;
    }

    private static Set<String> lower(List<String> values) {
        Set<String> out = new HashSet<>();
        if (values == null) return out;
        for (String v : values) {
            if (v != null && !v.isBlank()) out.add(v.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        for (String x : a) {
            if (b.contains(x)) return true;
        }
        return false;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
