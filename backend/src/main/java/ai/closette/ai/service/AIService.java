package ai.closette.ai.service;

import ai.closette.ai.dto.BeautyAnalysis;
import ai.closette.ai.dto.ClothingAnalysis;
import ai.closette.ai.dto.IngredientExplanation;

/**
 * Provider-independent AI seam (NFR-13). The rest of the backend depends only on
 * this interface — never on Qwen/OpenAI/Gemini specifics. The concrete
 * implementation talks to the FastAPI service, which selects the actual model.
 */
public interface AIService {

    ClothingAnalysis analyzeClothing(byte[] image, String filename, String contentType);

    BeautyAnalysis analyzeBeautyPhoto(byte[] image, String filename, String contentType);

    IngredientExplanation explainIngredient(String name);
}
