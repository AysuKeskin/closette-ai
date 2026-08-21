package ai.closette.ai.service;

import ai.closette.ai.dto.BeautyAnalysis;
import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.ai.dto.BuyAdvice;
import ai.closette.ai.dto.IngredientExplanation;
import ai.closette.ai.dto.OutfitCandidate;
import ai.closette.ai.dto.OutfitSuggestion;

import java.util.List;
import java.util.Map;

/**
 * Provider-independent AI seam (NFR-13). The rest of the backend depends only on
 * this interface — never on Qwen/OpenAI/Gemini specifics. The concrete
 * implementation talks to the FastAPI service, which selects the actual model.
 */
public interface AIService {

    ClothingAnalysis analyzeClothing(byte[] image, String filename, String contentType);

    BeautyAnalysis analyzeBeautyPhoto(byte[] image, String filename, String contentType);

    IngredientExplanation explainIngredient(String name);

    /** Visual-similarity embedding for an item image. Returns null on failure. */
    float[] embedItem(byte[] image, String filename, String contentType);

    /** RAG: compose one outfit from the retrieved owned items. Null on failure. */
    OutfitSuggestion generateOutfit(String occasion, List<OutfitCandidate> items, List<String> preferences);

    /** RAG: should-I-buy verdict from the candidate + similar owned items + scores. Null on failure. */
    BuyAdvice buyAdvice(Map<String, Object> candidate, List<Map<String, Object>> matches, Map<String, Object> scores);
}
