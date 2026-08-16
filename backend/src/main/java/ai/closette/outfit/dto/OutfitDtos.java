package ai.closette.outfit.dto;

import ai.closette.outfit.model.FeedbackSignal;
import ai.closette.outfit.model.Outfit;
import ai.closette.outfit.model.OutfitStatus;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response DTOs for the Get Ready / outfit domain (FR-07, FR-08, FR-14). */
public final class OutfitDtos {

    private OutfitDtos() {
    }

    /** FR-07 — free-text description of the occasion. */
    public record GetReadyRequest(
            String prompt,
            String occasion
    ) {
    }

    /** A generated (not-yet-saved) look plus a plain-language rationale (NFR-07). */
    public record GeneratedLook(
            String title,
            String rationale,
            List<WardrobeItemResponse> items
    ) {
    }

    public record SaveOutfitRequest(
            String title,
            String occasion,
            @NotNull List<UUID> itemIds,
            OutfitStatus status
    ) {
    }

    public record OutfitResponse(
            UUID id,
            String title,
            String occasion,
            OutfitStatus status,
            boolean favorite,
            List<WardrobeItemResponse> items,
            Instant createdAt
    ) {
        public static OutfitResponse from(Outfit o, List<WardrobeItemResponse> items) {
            return new OutfitResponse(o.getId(), o.getTitle(), o.getOccasion(), o.getStatus(),
                    o.isFavorite(), items, o.getCreatedAt());
        }
    }

    public record FeedbackRequest(
            UUID outfitId,
            @NotNull FeedbackSignal signal
    ) {
    }
}
