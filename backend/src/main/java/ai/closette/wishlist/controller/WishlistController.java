package ai.closette.wishlist.controller;

import ai.closette.common.api.ApiResponse;
import ai.closette.common.security.SecurityUtil;
import ai.closette.wishlist.dto.WishlistDtos.CreateWishlistItemRequest;
import ai.closette.wishlist.dto.WishlistDtos.WishlistItemResponse;
import ai.closette.wishlist.service.WishlistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService service;

    public WishlistController(WishlistService service) {
        this.service = service;
    }

    @PostMapping("/items")
    public ApiResponse<WishlistItemResponse> create(@Valid @RequestBody CreateWishlistItemRequest request) {
        return ApiResponse.ok(service.create(SecurityUtil.currentUserId(), request));
    }

    @GetMapping("/items")
    public ApiResponse<List<WishlistItemResponse>> list() {
        return ApiResponse.ok(service.list(SecurityUtil.currentUserId()));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtil.currentUserId(), id);
        return ApiResponse.ok(null);
    }
}
