"""Fashion-embedding seam for visual similarity ("do I own something like this?").

Default is a deterministic mock vector so the whole vector-search path (pgvector)
works end-to-end offline. Marqo-FashionSigLIP plugs in later (self-hosted) — same
interface, same 512-dim contract, no paid API.
"""
from __future__ import annotations

import hashlib
from abc import ABC, abstractmethod

import numpy as np

from app.core.config import get_settings

EMBED_DIM = 512


class Embedder(ABC):
    model_name: str = "unknown"

    @abstractmethod
    def embed_image(self, image_bytes: bytes) -> list[float]:
        ...


class MockEmbedder(Embedder):
    """Deterministic pseudo-embedding from the image bytes. NOT semantically
    meaningful, but stable per image and unit-normalised, so pgvector wiring,
    dedupe and tests all work before the real model exists."""

    model_name = "mock-hash-512"

    def embed_image(self, image_bytes: bytes) -> list[float]:
        seed = int.from_bytes(hashlib.sha256(image_bytes or b"seed").digest()[:8], "big")
        rng = np.random.default_rng(seed)
        vec = rng.standard_normal(EMBED_DIM)
        vec /= np.linalg.norm(vec) + 1e-9
        return vec.astype(float).tolist()


class FashionSigLIPEmbedder(Embedder):
    """Marqo-FashionSigLIP (self-hosted). Stub until weights are installed."""

    model_name = "marqo-fashionSigLIP"

    def embed_image(self, image_bytes: bytes) -> list[float]:
        raise NotImplementedError(
            "FashionSigLIP not installed. Set EMBED_PROVIDER=mock, or add the "
            "model on a GPU host."
        )


def get_embedder() -> Embedder:
    provider = get_settings().embed_provider.lower()
    if provider in ("fashionsiglip", "siglip"):
        return FashionSigLIPEmbedder()
    return MockEmbedder()
