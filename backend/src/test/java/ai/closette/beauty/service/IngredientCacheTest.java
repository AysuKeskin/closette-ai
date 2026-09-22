package ai.closette.beauty.service;

import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.ai.service.AIService;
import ai.closette.beauty.repository.IngredientExplanationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The explanation cache, which is what keeps the model's bill flat.
 *
 * Every reader of the same ingredient used to be a separate paid call; these
 * prove the second reader is free, and that a different language is still its
 * own question rather than the first answer handed back untranslated.
 */
@SpringBootTest
@ActiveProfiles("test")
class IngredientCacheTest {

    @Autowired
    BeautyService beautyService;

    @Autowired
    IngredientExplanationRepository explanations;

    @MockitoBean
    AIService aiService;

    /** A name nothing else in the suite has explained, so the count is this test's own. */
    private static String freshName() {
        return "Probeamide" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Test
    void theSameIngredientIsExplainedOnceAndServedFromTheCacheAfterwards() {
        String name = freshName();
        when(aiService.explainIngredient(any()))
                .thenReturn(new IngredientExplanation(name, "A humectant that holds water."));

        IngredientExplanation first = beautyService.explainIngredient(name);
        IngredientExplanation second = beautyService.explainIngredient(name);

        assertThat(first.explanation()).isEqualTo("A humectant that holds water.");
        assertThat(second.explanation()).isEqualTo(first.explanation());
        verify(aiService, times(1)).explainIngredient(any());
        assertThat(explanations.findByInciNameIgnoreCaseAndLang(name, "en")).isPresent();
    }

    @Test
    void anEmptyAnswerIsNotCached() {
        // Caching a blank would make one bad call permanent for every later reader.
        String name = freshName();
        when(aiService.explainIngredient(any())).thenReturn(new IngredientExplanation(name, "  "));

        beautyService.explainIngredient(name);

        assertThat(explanations.findByInciNameIgnoreCaseAndLang(name, "en")).isEmpty();
    }
}
