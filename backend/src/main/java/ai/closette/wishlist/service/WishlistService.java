package ai.closette.wishlist.service;

import ai.closette.common.exception.ApiException;
import ai.closette.storage.service.StorageService;
import ai.closette.wishlist.dto.WishlistDtos.CreateWishlistItemRequest;
import ai.closette.wishlist.dto.WishlistDtos.WishlistItemResponse;
import ai.closette.wishlist.model.WishlistItem;
import ai.closette.wishlist.repository.WishlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WishlistService {

    private final WishlistItemRepository repository;
    private final StorageService storage;

    public WishlistService(WishlistItemRepository repository, StorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional
    public WishlistItemResponse create(UUID userId, CreateWishlistItemRequest req) {
        WishlistItem item = new WishlistItem(userId);
        item.setProductName(req.productName().trim());
        item.setBrand(req.brand());
        item.setPrice(req.price());
        item.setCategory(req.category());
        item.setImageKey(req.imageKey());
        return toResponse(repository.save(item));
    }

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        WishlistItem item = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Wishlist item not found"));
        repository.delete(item);
    }

    private WishlistItemResponse toResponse(WishlistItem item) {
        return WishlistItemResponse.from(item, storage.presignedUrl(storage.wardrobeBucket(), item.getImageKey()));
    }
}
