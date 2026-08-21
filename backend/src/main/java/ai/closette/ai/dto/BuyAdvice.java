package ai.closette.ai.dto;

/** LLM shopping verdict (RAG: grounded in similar owned items + computed scores). */
public record BuyAdvice(
        String verdict,      // buy | maybe | skip
        String explanation
) {
}
