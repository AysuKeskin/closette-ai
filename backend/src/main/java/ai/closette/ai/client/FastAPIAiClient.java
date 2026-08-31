package ai.closette.ai.client;

import ai.closette.ai.dto.BeautyAnalysis;
import ai.closette.ai.dto.BuyAdvice;
import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.ai.dto.IngredientsResult;
import ai.closette.ai.dto.OutfitCandidate;
import ai.closette.ai.dto.OutfitSuggestion;
import ai.closette.ai.service.AIService;

import java.util.List;
import ai.closette.common.exception.ApiException;
import ai.closette.common.i18n.Messages;
import ai.closette.common.exception.MessageKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * {@link AIService} implementation that delegates to the FastAPI service over HTTP.
 *
 * Prose endpoints carry the caller's language so a model-written rationale reads
 * in the same language as the rest of the screen; the attribute endpoints do not,
 * because what they return is catalogued, not read.
 * Any transport/parse failure is surfaced as {@link ApiException#aiUnavailable} so
 * callers can offer the "add manually" fallback (NFR-06) instead of a hard 500.
 */
@Service
public class FastAPIAiClient implements AIService {

    private static final Logger log = LoggerFactory.getLogger(FastAPIAiClient.class);

    private final WebClient aiWebClient;

    public FastAPIAiClient(WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }

    @Override
    public ClothingAnalysis analyzeClothing(byte[] image, String filename, String contentType) {
        return postImage("/analyze/clothing", image, filename, contentType, ClothingAnalysis.class);
    }

    @Override
    public ClothingAnalysis parseClothingText(String description) {
        try {
            return aiWebClient.post()
                    .uri("/analyze/clothing-text")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("description", description))
                    .retrieve()
                    .bodyToMono(ClothingAnalysis.class)
                    .block();
        } catch (Exception e) {
            log.warn("AI clothing-text parse failed", e);
            return null;
        }
    }

    @Override
    public List<String> extractIngredients(byte[] image, String filename, String contentType) {
        try {
            IngredientsResult result = postImage("/analyze/ingredients", image, filename, contentType, IngredientsResult.class);
            return result != null && result.ingredients() != null ? result.ingredients() : List.of();
        } catch (Exception e) {
            log.warn("AI ingredient OCR failed", e);
            return List.of();
        }
    }

    @Override
    public BeautyAnalysis analyzeBeautyPhoto(byte[] image, String filename, String contentType) {
        return postImage("/analyze/beauty", image, filename, contentType, BeautyAnalysis.class);
    }

    @Override
    public float[] embedItem(byte[] image, String filename, String contentType) {
        // Best-effort: a missing embedding must never fail item creation.
        try {
            EmbedResult result = postImage("/embed/item", image, filename, contentType, EmbedResult.class);
            return result != null ? result.vector() : null;
        } catch (Exception e) {
            log.warn("AI embedding failed for {}", filename, e);
            return null;
        }
    }

    private record EmbedResult(String model, int dim, float[] vector) {
    }

    @Override
    public OutfitSuggestion generateOutfit(String occasion, List<OutfitCandidate> items, List<String> preferences) {
        try {
            return aiWebClient.post()
                    .uri("/generate/outfit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "occasion", occasion == null ? "" : occasion,
                            "items", items,
                            "preferences", preferences == null ? List.of() : preferences,
                            "lang", Messages.currentLanguageTag()))
                    .retrieve()
                    .bodyToMono(OutfitSuggestion.class)
                    .block();
        } catch (Exception e) {
            log.warn("AI outfit generation failed", e);
            return null; // caller falls back to the rule-based composer
        }
    }

    @Override
    public BuyAdvice buyAdvice(Map<String, Object> candidate, List<Map<String, Object>> matches,
                              Map<String, Object> scores) {
        try {
            return aiWebClient.post()
                    .uri("/generate/buy-advice")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("candidate", candidate, "matches", matches, "scores", scores,
                            "lang", Messages.currentLanguageTag()))
                    .retrieve()
                    .bodyToMono(BuyAdvice.class)
                    .block();
        } catch (Exception e) {
            log.warn("AI buy-advice failed", e);
            return null;
        }
    }

    @Override
    public IngredientExplanation explainIngredient(String name) {
        try {
            return aiWebClient.post()
                    .uri("/ingredients/explain")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("name", name, "lang", Messages.currentLanguageTag()))
                    .retrieve()
                    .bodyToMono(IngredientExplanation.class)
                    .block();
        } catch (Exception e) {
            log.warn("AI ingredient explanation failed", e);
            throw ApiException.aiUnavailable(MessageKeys.AI_UNAVAILABLE);
        }
    }

    private <T> T postImage(String path, byte[] image, String filename, String contentType, Class<T> type) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            ByteArrayResource resource = new ByteArrayResource(image) {
                @Override
                public String getFilename() {
                    return filename == null ? "upload" : filename;
                }
            };
            builder.part("file", resource)
                    .contentType(contentType != null
                            ? MediaType.parseMediaType(contentType)
                            : MediaType.APPLICATION_OCTET_STREAM);

            return aiWebClient.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(type)
                    .block();
        } catch (Exception e) {
            log.warn("AI request to {} failed", path, e);
            throw ApiException.aiUnavailable(MessageKeys.AI_UNAVAILABLE_ADD_MANUALLY);
        }
    }
}
