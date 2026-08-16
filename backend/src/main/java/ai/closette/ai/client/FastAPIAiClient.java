package ai.closette.ai.client;

import ai.closette.ai.dto.BeautyAnalysis;
import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.ai.service.AIService;
import ai.closette.common.exception.ApiException;
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
    public BeautyAnalysis analyzeBeautyPhoto(byte[] image, String filename, String contentType) {
        return postImage("/analyze/beauty", image, filename, contentType, BeautyAnalysis.class);
    }

    @Override
    public IngredientExplanation explainIngredient(String name) {
        try {
            return aiWebClient.post()
                    .uri("/ingredients/explain")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("name", name))
                    .retrieve()
                    .bodyToMono(IngredientExplanation.class)
                    .block();
        } catch (Exception e) {
            log.warn("AI ingredient explanation failed", e);
            throw ApiException.aiUnavailable("AI analysis is temporarily unavailable.");
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
            throw ApiException.aiUnavailable("AI analysis is temporarily unavailable. You can add this item manually.");
        }
    }
}
