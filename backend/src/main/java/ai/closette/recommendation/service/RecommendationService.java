package ai.closette.recommendation.service;

import ai.closette.ai.dto.BuyAdvice;
import ai.closette.ai.service.AIService;
import ai.closette.auth.service.AccountGuard;
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
 * FR-09 preference profile + FR-10 "Should I buy this?". Both are rule-based and
 * explainable (NFR-07) — no LLM calls (NFR-14).
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

    public RecommendationService(PreferenceWeightRepository weightRepository,
                                 OutfitFeedbackRepository feedbackRepository,
                                 OutfitRepository outfitRepository,
                                 WardrobeItemRepository wardrobeRepository,
                                 StorageService storage,
                                 AccountGuard accountGuard,
                                 AIService aiService) {
        this.weightRepository = weightRepository;
        this.feedbackRepository = feedbackRepository;
        this.outfitRepository = outfitRepository;
        this.wardrobeRepository = wardrobeRepository;
        this.storage = storage;
        this.accountGuard = accountGuard;
        this.aiService = aiService;
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

    /** FR-10 — compare a candidate purchase against the user's wardrobe. */
    @Transactional(readOnly = true)
    public ShouldIBuyResponse shouldIBuy(UUID userId, ShouldIBuyRequest req) {
        accountGuard.requireVerifiedEmail(userId); // gated on soft email verification
        List<WardrobeItem> wardrobe = wardrobeRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Set<String> candidateColors = lower(req.colors());
        Set<String> candidateStyles = lower(req.styles());

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
            if (req.category() != null && item.getCategory() == req.category() && colorMatch) {
                similar.add(item);
            }
        }

        int total = wardrobe.size();
        int score = total == 0 ? 0 : (int) Math.round(100.0 * matching.size() / total);
        score = Math.min(100, score);

        String explanation = buildExplanation(total, matching.size(), similar.size(), matchByCategory);

        List<WardrobeItemResponse> similarResponses = similar.stream()
                .limit(6)
                .map(i -> WardrobeItemResponse.from(i, storage.presignedUrl(storage.wardrobeBucket(), i.getImageKey())))
                .toList();

        // RAG: retrieved similar owned items + code-computed scores → LLM verdict.
        BuyAdvice advice = aiService.buyAdvice(
                candidateContext(req),
                matchContext(matching),
                Map.of("matchScore", score,
                        "matchingItemCount", matching.size(),
                        "similarItemCount", similar.size()));
        String verdict = advice != null && advice.verdict() != null
                ? advice.verdict() : ruleVerdict(score, similar.size());
        String finalExplanation = advice != null && advice.explanation() != null && !advice.explanation().isBlank()
                ? advice.explanation() : explanation;

        return new ShouldIBuyResponse(score, verdict, matching.size(), similar.size(), similarResponses, finalExplanation);
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

    private static String ruleVerdict(int score, int similarCount) {
        if (similarCount >= 2) return "skip";
        if (score >= 40) return "buy";
        return "maybe";
    }

    // ---- helpers ----

    private String buildExplanation(int total, int matchCount, int similarCount,
                                    Map<ClothingCategory, Integer> byCategory) {
        if (total == 0) {
            return "Your wardrobe is empty, so there's nothing to compare against yet.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(matchCount == 0
                ? "This doesn't obviously match anything you own yet."
                : "Works with " + matchCount + " item" + (matchCount == 1 ? "" : "s") + " you already own");
        if (!byCategory.isEmpty()) {
            List<String> parts = new ArrayList<>();
            byCategory.forEach((cat, count) -> parts.add(count + " " + cat.name().toLowerCase(Locale.ROOT)));
            sb.append(" (").append(String.join(", ", parts)).append(")");
        }
        sb.append(".");
        if (similarCount > 0) {
            sb.append(" You already have ").append(similarCount)
                    .append(" similar item").append(similarCount == 1 ? "" : "s").append(".");
        }
        return sb.toString();
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
