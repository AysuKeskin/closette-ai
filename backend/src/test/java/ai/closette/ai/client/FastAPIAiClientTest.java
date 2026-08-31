package ai.closette.ai.client;

import ai.closette.ai.dto.BeautyAnalysis;
import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.common.exception.ApiException;
import ai.closette.common.exception.ErrorCode;
import ai.closette.common.exception.MessageKeys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The AI seam over HTTP, driven by a stubbed exchange function — no FastAPI
 * service and no network. What matters here is the mapping of the AI service's
 * payload and, above all, how failures degrade (NFR-06).
 */
class FastAPIAiClientTest {

    private static final byte[] IMAGE = "image-bytes".getBytes();

    private static FastAPIAiClient clientReturning(String json) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(json)
                        .build()))
                .build();
        return new FastAPIAiClient(webClient);
    }

    private static FastAPIAiClient unreachableClient() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new IOException("connection refused")))
                .build();
        return new FastAPIAiClient(webClient);
    }

    @Test
    void clothingAnalysisIsMappedIncludingTheColorBreakdown() {
        ClothingAnalysis analysis = clientReturning("""
                {"category":"dress","subcategory":"mini dress","colors":["navy"],
                 "color_details":[{"name":"navy","hex":"#22314E","percentage":100}],
                 "pattern":"solid","styles":["minimal"],"seasons":["spring"],"confidence":0.72}
                """).analyzeClothing(IMAGE, "item.jpg", "image/jpeg");

        assertThat(analysis.category()).isEqualTo("dress");
        assertThat(analysis.colors()).containsExactly("navy");
        assertThat(analysis.confidence()).isEqualTo(0.72);
        // snake_case on the wire, camelCase in Java — a mismatch here silently
        // drops the colour swatches in the confirm screen.
        assertThat(analysis.colorDetails()).singleElement()
                .satisfies(color -> {
                    assertThat(color.name()).isEqualTo("navy");
                    assertThat(color.hex()).isEqualTo("#22314E");
                    assertThat(color.percentage()).isEqualTo(100);
                });
    }

    @Test
    void beautyAnalysisIsMapped() {
        BeautyAnalysis analysis = clientReturning("""
                {"brand":"CeraVe","productName":"Hydrating Cleanser","category":"skincare","confidence":0.61}
                """).analyzeBeautyPhoto(IMAGE, "cream.jpg", "image/jpeg");

        assertThat(analysis.brand()).isEqualTo("CeraVe");
        assertThat(analysis.productName()).isEqualTo("Hydrating Cleanser");
    }

    @Test
    void ingredientExplanationIsMapped() {
        IngredientExplanation explanation = clientReturning("""
                {"name":"Niacinamide","explanation":"A form of vitamin B3."}
                """).explainIngredient("Niacinamide");

        assertThat(explanation.name()).isEqualTo("Niacinamide");
        assertThat(explanation.explanation()).isEqualTo("A form of vitamin B3.");
    }

    @Test
    void embeddingIsMapped() {
        float[] vector = clientReturning("""
                {"model":"mock-hash-512","dim":3,"vector":[0.1,0.2,0.3]}
                """).embedItem(IMAGE, "item.jpg", "image/jpeg");

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void unreachableAiServiceBecomesAnAddManuallyError() {
        // The client branches on AI_UNAVAILABLE to offer manual entry, so the
        // code must survive the trip — a raw 500 would strand the user.
        assertThatThrownBy(() -> unreachableClient().analyzeClothing(IMAGE, "item.jpg", "image/jpeg"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getMessageKey()).isEqualTo(MessageKeys.AI_UNAVAILABLE_ADD_MANUALLY);
                });
    }

    @Test
    void unreachableAiServiceAlsoFailsSoftlyForIngredients() {
        assertThatThrownBy(() -> unreachableClient().explainIngredient("Niacinamide"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    @Test
    void failedEmbeddingReturnsNullInsteadOfThrowing() {
        // Similarity is a nice-to-have; throwing here would break item creation.
        assertThat(unreachableClient().embedItem(IMAGE, "item.jpg", "image/jpeg")).isNull();
    }
}
